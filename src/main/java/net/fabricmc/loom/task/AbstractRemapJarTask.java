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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.ZipUtils;

public abstract class AbstractRemapJarTask extends Jar {
	public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	public static final String MANIFEST_NAMESPACE_KEY = "Fabric-Mapping-Namespace";
	public static final String MANIFEST_SPLIT_ENV_KEY = "Fabric-Loom-Split-Environment";
	public static final String MANIFEST_CLIENT_ENTRIES_KEY = "Fabric-Loom-Client-Only-Entries";
	public static final Attributes.Name MANIFEST_SPLIT_ENV_NAME = new Attributes.Name(MANIFEST_SPLIT_ENV_KEY);
	public static final Attributes.Name MANIFEST_CLIENT_ENTRIES_NAME = new Attributes.Name(MANIFEST_CLIENT_ENTRIES_KEY);

	@InputFile
	public abstract RegularFileProperty getInputFile();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	/**
	 * When enabled the TinyRemapperService will not be shared across sub projects.
	 */
	@Input
	public abstract Property<Boolean> getRemapperIsolation();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Input
	public abstract Property<Boolean> getIncludesClientOnlyClasses();

	@Input
	public abstract ListProperty<String> getAdditionalClientOnlyEntries();

	@Inject
	public AbstractRemapJarTask() {
		getSourceNamespace().convention(MappingsNamespace.NAMED.toString()).finalizeValueOnRead();
		getTargetNamespace().convention(MappingsNamespace.INTERMEDIARY.toString()).finalizeValueOnRead();
		getRemapperIsolation().convention(false).finalizeValueOnRead();
		getIncludesClientOnlyClasses().convention(false).finalizeValueOnRead();
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

			params.getJarManifestService().set(JarManifestService.get(getProject()));

			if (getIncludesClientOnlyClasses().get()) {
				final List<String> clientOnlyEntries = new ArrayList<>(getClientOnlyEntries());
				clientOnlyEntries.addAll(getAdditionalClientOnlyEntries().get());
				applyClientOnlyManifestAttributes(params, clientOnlyEntries);
				params.getClientOnlyEntries().set(clientOnlyEntries.stream().filter(s -> s.endsWith(".class")).toList());
			}

			action.execute(params);
		});
	}

	@Internal
	protected abstract List<String> getClientOnlyEntries();

	public interface AbstractRemapParams extends WorkParameters {
		RegularFileProperty getInputFile();
		RegularFileProperty getOutputFile();

		Property<String> getSourceNamespace();
		Property<String> getTargetNamespace();

		Property<Boolean> getArchivePreserveFileTimestamps();
		Property<Boolean> getArchiveReproducibleFileOrder();

		Property<JarManifestService> getJarManifestService();
		MapProperty<String, String> getManifestAttributes();

		ListProperty<String> getClientOnlyEntries();
	}

	protected void applyClientOnlyManifestAttributes(AbstractRemapParams params, List<String> entries) {
		params.getManifestAttributes().set(Map.of(
				MANIFEST_SPLIT_ENV_KEY, "true",
				MANIFEST_CLIENT_ENTRIES_KEY, String.join(";", entries)
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
			int count = ZipUtils.transform(outputFile, Map.of(MANIFEST_PATH, bytes -> {
				var manifest = new Manifest(new ByteArrayInputStream(bytes));

				getParameters().getJarManifestService().get().apply(manifest, getParameters().getManifestAttributes().get());
				manifest.getMainAttributes().putValue(MANIFEST_NAMESPACE_KEY, getParameters().getTargetNamespace().get());

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Preconditions.checkState(count > 0, "Did not transform any jar manifest");
		}

		protected void rewriteJar() throws IOException {
			final boolean isReproducibleFileOrder = getParameters().getArchiveReproducibleFileOrder().get();
			final boolean isPreserveFileTimestamps = getParameters().getArchivePreserveFileTimestamps().get();

			if (isReproducibleFileOrder || !isPreserveFileTimestamps) {
				ZipReprocessorUtil.reprocessZip(outputFile.toFile(), isReproducibleFileOrder, isPreserveFileTimestamps);
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
}
