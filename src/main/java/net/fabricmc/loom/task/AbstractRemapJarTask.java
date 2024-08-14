/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Manifest;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class AbstractRemapJarTask extends Jar {
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

	@Inject
	protected abstract BuildEventsListenerRegistry getBuildEventsListenerRegistry();

	@Input
	public abstract Property<Boolean> getIncludesClientOnlyClasses();

	@Input
	public abstract ListProperty<String> getAdditionalClientOnlyEntries();

	@Input
	@Optional
	public abstract Property<String> getClientOnlySourceSetName();

	@Input
	@Optional
	@ApiStatus.Internal
	public abstract Property<String> getJarType();

	private final Provider<JarManifestService> jarManifestServiceProvider;

	@Inject
	public AbstractRemapJarTask() {
		getSourceNamespace().convention(MappingsNamespace.NAMED.toString()).finalizeValueOnRead();
		getTargetNamespace().convention(MappingsNamespace.INTERMEDIARY.toString()).finalizeValueOnRead();
		getIncludesClientOnlyClasses().convention(false).finalizeValueOnRead();
		getJarType().finalizeValueOnRead();

		jarManifestServiceProvider = JarManifestService.get(getProject());
		usesService(jarManifestServiceProvider);
	}

	public final <P extends AbstractRemapParams> void submitWork(Class<? extends AbstractRemapAction<P>> workAction, Action<P> action) {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(workAction, params -> {
			params.getInputFile().set(getInputFile());
			params.getOutputFile().set(getArchiveFile());

			params.getSourceNamespace().set(getSourceNamespace());
			params.getTargetNamespace().set(getTargetNamespace());

			params.getArchivePreserveFileTimestamps().set(isPreserveFileTimestamps());
			params.getArchiveReproducibleFileOrder().set(isReproducibleFileOrder());

			params.getJarManifestService().set(jarManifestServiceProvider);
			params.getEntryCompression().set(getEntryCompression());

			if (getIncludesClientOnlyClasses().get()) {
				final List<String> clientOnlyEntries = new ArrayList<>(getClientOnlyEntries(getClientSourceSet()));
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

	protected abstract List<String> getClientOnlyEntries(SourceSet sourceSet);

	public interface AbstractRemapParams extends WorkParameters {
		RegularFileProperty getInputFile();
		RegularFileProperty getOutputFile();

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
		protected final Path inputFile;
		protected final Path outputFile;

		@Inject
		public AbstractRemapAction() {
			inputFile = getParameters().getInputFile().getAsFile().get().toPath();
			outputFile = getParameters().getOutputFile().getAsFile().get().toPath();
		}

		protected void modifyJarManifest() throws IOException {
			int count = ZipUtils.transform(outputFile, Map.of(Constants.Manifest.PATH, bytes -> {
				var manifest = new Manifest(new ByteArrayInputStream(bytes));

				getParameters().getJarManifestService().get().apply(manifest, getParameters().getManifestAttributes().get());
				manifest.getMainAttributes().putValue(Constants.Manifest.MAPPING_NAMESPACE, getParameters().getTargetNamespace().get());

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Preconditions.checkState(count > 0, "Did not transform any jar manifest");
		}

		protected void rewriteJar() throws IOException {
			final boolean isReproducibleFileOrder = getParameters().getArchiveReproducibleFileOrder().get();
			final boolean isPreserveFileTimestamps = getParameters().getArchivePreserveFileTimestamps().get();
			final ZipEntryCompression compression = getParameters().getEntryCompression().get();

			if (isReproducibleFileOrder || !isPreserveFileTimestamps || compression != ZipEntryCompression.DEFLATED) {
				ZipReprocessorUtil.reprocessZip(outputFile, isReproducibleFileOrder, isPreserveFileTimestamps, compression);
			}
		}
	}

	@Deprecated
	@InputFile
	public RegularFileProperty getInput() {
		return getInputFile();
	}

	protected static List<String> getRootPaths(Set<File> files) {
		return files.stream()
				.map(root -> {
					String rootPath = root.getAbsolutePath().replace("\\", "/");

					if (rootPath.charAt(rootPath.length() - 1) != '/') {
						rootPath += '/';
					}

					return rootPath;
				}).toList();
	}

	protected static Function<File, String> relativePath(List<String> rootPaths) {
		return file -> {
			String s = file.getAbsolutePath().replace("\\", "/");

			for (String rootPath : rootPaths) {
				if (s.startsWith(rootPath)) {
					s = s.substring(rootPath.length());
				}
			}

			return s;
		};
	}

	@ApiStatus.Internal
	@Internal
	protected LoomGradleExtension getLoomExtension() {
		return LoomGradleExtension.get(getProject());
	}

	private SourceSet getClientSourceSet() {
		Preconditions.checkArgument(LoomGradleExtension.get(getProject()).areEnvironmentSourceSetsSplit(), "Cannot get client sourceset as project is not split");
		return SourceSetHelper.getSourceSetByName(getClientOnlySourceSetName().get(), getProject());
	}
}
