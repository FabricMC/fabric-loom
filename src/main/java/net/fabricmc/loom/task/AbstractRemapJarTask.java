/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2025 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.service.ClientEntriesService;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

public abstract class AbstractRemapJarTask extends Jar {
	/**
	 * The main input jar to remap.
	 * Other contents can be added to this task, but this jar must always be present.
	 *
	 * <p>The input file's manifest will be copied into the remapped jar.
	 */
	@InputFile
	public abstract RegularFileProperty getInputFile();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Input
	public abstract Property<Boolean> getIncludesClientOnlyClasses();

	@Input
	public abstract ListProperty<String> getAdditionalClientOnlyEntries();

	@Input
	@Optional
	public abstract Property<String> getClientOnlySourceSetName();

	/**
	 * Optionally supply a single mapping file or jar file containing mappings to be used for remapping.
	 */
	@ApiStatus.Experimental
	@InputFiles
	@Optional
	public abstract ConfigurableFileCollection getCustomMappings();

	@Input
	@Optional
	@ApiStatus.Internal
	public abstract Property<String> getJarType();

	@Nested
	@Optional
	protected abstract Property<ClientEntriesService.Options> getClientEntriesServiceOptions();

	private final Provider<JarManifestService> jarManifestServiceProvider;

	@Inject
	public AbstractRemapJarTask() {
		from(getProject().zipTree(getInputFile()));
		getSourceNamespace().convention(MappingsNamespace.NAMED.toString()).finalizeValueOnRead();
		getTargetNamespace().convention(MappingsNamespace.INTERMEDIARY.toString()).finalizeValueOnRead();
		getIncludesClientOnlyClasses().convention(false).finalizeValueOnRead();
		getJarType().finalizeValueOnRead();

		getClientEntriesServiceOptions().set(getIncludesClientOnlyClasses().flatMap(clientOnlyEntries -> {
			if (clientOnlyEntries) {
				return getClientOnlyEntriesOptionsProvider(getClientSourceSet());
			}

			// Empty
			return getProject().getObjects().property(ClientEntriesService.Options.class);
		}));

		jarManifestServiceProvider = JarManifestService.get(getProject());
		usesService(jarManifestServiceProvider);
	}

	public final <P extends AbstractRemapParams> void submitWork(Class<? extends AbstractRemapAction<P>> workAction, Action<P> action) {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(workAction, params -> {
			params.getInputFile().set(getInputFile());
			params.getArchiveFile().set(getArchiveFile());

			params.getSourceNamespace().set(getSourceNamespace());
			params.getTargetNamespace().set(getTargetNamespace());

			params.getArchivePreserveFileTimestamps().set(isPreserveFileTimestamps());
			params.getArchiveReproducibleFileOrder().set(isReproducibleFileOrder());

			params.getJarManifestService().set(jarManifestServiceProvider);
			params.getEntryCompression().set(getEntryCompression());

			if (getIncludesClientOnlyClasses().get()) {
				final List<String> clientOnlyEntries;

				try (var serviceFactory = new ScopedServiceFactory()) {
					ClientEntriesService<ClientEntriesService.Options> service = serviceFactory.get(getClientEntriesServiceOptions());
					clientOnlyEntries = new ArrayList<>(service.getClientOnlyEntries());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				clientOnlyEntries.addAll(getAdditionalClientOnlyEntries().get());
				Collections.sort(clientOnlyEntries);
				applyClientOnlyManifestAttributes(params, clientOnlyEntries);
				params.getClientOnlyEntries().set(clientOnlyEntries.stream().filter(s -> s.endsWith(".class")).toList());
			}

			if (getJarType().isPresent()) {
				params.getManifestAttributes().put(Constants.Manifest.JAR_TYPE, getJarType().get());
			}

			action.execute(params);
		});
	}

	protected abstract Provider<? extends ClientEntriesService.Options> getClientOnlyEntriesOptionsProvider(SourceSet clientSourceSet);

	public interface AbstractRemapParams extends WorkParameters {
		RegularFileProperty getInputFile();
		RegularFileProperty getArchiveFile();

		Property<String> getSourceNamespace();
		Property<String> getTargetNamespace();

		/**
		 * Checks whether {@link #getSourceNamespace()} and {@link #getTargetNamespace()}
		 * have the same value. When this is {@code true}, the user does not intend for any
		 * remapping to occur. They are using the task for its other features, such as adding
		 * namespace to the manifest, nesting jars, reproducible builds, etc.
		 *
		 * @return whether the source and target namespaces match
		 */
		default boolean namespacesMatch() {
			return this.getSourceNamespace().get().equals(this.getTargetNamespace().get());
		}

		Property<Boolean> getArchivePreserveFileTimestamps();
		Property<Boolean> getArchiveReproducibleFileOrder();
		Property<ZipEntryCompression> getEntryCompression();

		Property<JarManifestService> getJarManifestService();
		MapProperty<String, String> getManifestAttributes();

		ListProperty<String> getClientOnlyEntries();
	}

	protected void applyClientOnlyManifestAttributes(AbstractRemapParams params, List<String> entries) {
		params.getManifestAttributes().set(Map.of(
				Constants.Manifest.SPLIT_ENV, "true",
				Constants.Manifest.CLIENT_ENTRIES, String.join(";", entries)
		));
	}

	public abstract static class AbstractRemapAction<T extends AbstractRemapParams> implements WorkAction<T> {
		private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRemapAction.class);
		protected final Path outputFile;

		@Inject
		public AbstractRemapAction() {
			outputFile = getParameters().getArchiveFile().getAsFile().get().toPath();
		}

		@Override
		public final void execute() {
			try {
				Path tempInput = Files.createTempFile("loom-remapJar-", "-input.jar");
				Files.copy(outputFile, tempInput, StandardCopyOption.REPLACE_EXISTING);
				execute(tempInput);
				Files.delete(tempInput);
			} catch (Exception e) {
				try {
					Files.deleteIfExists(outputFile);
				} catch (IOException ex) {
					LOGGER.error("Failed to delete output file", ex);
				}

				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to remap " + outputFile.toAbsolutePath(), e);
			}
		}

		// Note: the inputFile parameter is the remapping input file.
		// The main input jar is available in the parameters, but should not be used
		// for remapping as it might be missing some files added manually to this task.
		protected abstract void execute(Path inputFile) throws IOException;

		protected void modifyJarManifest() throws IOException {
			int count = ZipUtils.transform(outputFile, Map.of(Constants.Manifest.PATH, bytes -> {
				var manifest = new Manifest(new ByteArrayInputStream(bytes));
				byte[] sourceManifestBytes = ZipUtils.unpackNullable(getParameters().getInputFile().get().getAsFile().toPath(), Constants.Manifest.PATH);

				if (sourceManifestBytes != null) {
					var sourceManifest = new Manifest(new ByteArrayInputStream(sourceManifestBytes));
					mergeManifests(manifest, sourceManifest);
				}

				getParameters().getJarManifestService().get().apply(manifest, getParameters().getManifestAttributes().get());
				manifest.getMainAttributes().putValue(Constants.Manifest.MAPPING_NAMESPACE, getParameters().getTargetNamespace().get());

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Check.require(count > 0, "Did not transform any jar manifest");
		}

		protected void rewriteJar() throws IOException {
			final boolean isReproducibleFileOrder = getParameters().getArchiveReproducibleFileOrder().get();
			final boolean isPreserveFileTimestamps = getParameters().getArchivePreserveFileTimestamps().get();
			final ZipEntryCompression compression = getParameters().getEntryCompression().get();

			if (isReproducibleFileOrder || !isPreserveFileTimestamps || compression != ZipEntryCompression.DEFLATED) {
				ZipReprocessorUtil.reprocessZip(outputFile, isReproducibleFileOrder, isPreserveFileTimestamps, compression);
			}
		}

		private static void mergeManifests(Manifest target, Manifest source) {
			mergeAttributes(target.getMainAttributes(), source.getMainAttributes());

			source.getEntries().forEach((name, sourceAttributes) -> {
				final Attributes targetAttributes = target.getAttributes(name);

				if (targetAttributes != null) {
					mergeAttributes(targetAttributes, sourceAttributes);
				} else {
					target.getEntries().put(name, sourceAttributes);
				}
			});
		}

		private static void mergeAttributes(Attributes target, Attributes source) {
			source.forEach(target::putIfAbsent);
		}
	}

	@Deprecated
	@InputFile
	public RegularFileProperty getInput() {
		return getInputFile();
	}

	private SourceSet getClientSourceSet() {
		Check.require(LoomGradleExtension.get(getProject()).areEnvironmentSourceSetsSplit(), "Cannot get client sourceset as project is not split");
		return SourceSetHelper.getSourceSetByName(getClientOnlySourceSetName().get(), getProject());
	}
}
