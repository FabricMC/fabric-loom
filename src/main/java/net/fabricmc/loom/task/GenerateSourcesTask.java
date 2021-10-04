/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.accesswidener.TransitiveAccessWidenerMappingsProcessor;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.IOStringConsumer;
import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.loom.util.gradle.ThreadedProgressLoggerConsumer;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;
import net.fabricmc.loom.util.ipc.IPCClient;
import net.fabricmc.loom.util.ipc.IPCServer;
import net.fabricmc.stitch.util.StitchUtil;

public abstract class GenerateSourcesTask extends AbstractLoomTask {
	public final LoomDecompiler decompiler;

	@InputFile
	public abstract RegularFileProperty getInputJar();

	@Inject
	public abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public GenerateSourcesTask(LoomDecompiler decompiler) {
		this.decompiler = decompiler;

		Objects.requireNonNull(getDecompilerConstructor(this.decompiler.getClass().getCanonicalName()),
				"%s must have a no args constructor".formatted(this.decompiler.getClass().getCanonicalName()));

		getOutputs().upToDateWhen((o) -> false);
	}

	@TaskAction
	public void run() throws IOException {
		if (!OperatingSystem.is64Bit()) {
			throw new UnsupportedOperationException("GenSources task requires a 64bit JVM to run due to the memory requirements.");
		}

		// Fork the JVM with 4G of ram
		final WorkQueue workQueue = createWorkQueue();

		// Set up the IPC path to get the log output back from the forked JVM
		final Path ipcPath = Files.createTempFile("loom", "ipc");
		Files.deleteIfExists(ipcPath);

		try (ThreadedProgressLoggerConsumer loggerConsumer = new ThreadedProgressLoggerConsumer(getProject(), decompiler.name(), "Decompiling minecraft sources");
				IPCServer logReceiver = new IPCServer(ipcPath, loggerConsumer)) {
			workQueue.submit(DecompileAction.class, params -> {
				params.getDecompilerClass().set(decompiler.getClass().getCanonicalName());

				params.getInputJar().set(getInputJar());
				params.getRuntimeJar().set(getExtension().getMappingsProvider().mappedProvider.getMappedJar());
				params.getSourcesDestinationJar().set(getMappedJarFileWithSuffix("-sources.jar"));
				params.getLinemap().set(getMappedJarFileWithSuffix("-sources.lmap"));
				params.getLinemapJar().set(getMappedJarFileWithSuffix("-linemapped.jar"));
				params.getMappings().set(getMappings().toFile());

				params.getIPCPath().set(ipcPath.toFile());

				params.getClassPath().plus(getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES));
			});

			workQueue.await();
		} catch (InterruptedException e) {
			throw new RuntimeException("Failed to shutdown log receiver", e);
		}
	}

	private WorkQueue createWorkQueue() {
		if (Boolean.getBoolean("fabric.loom.genSources.debug")) {
			// Useful if you want to debug the decompiler, make sure you run gradle with enough memory.
			return getWorkerExecutor().noIsolation();
		}

		return getWorkerExecutor().processIsolation(spec -> {
			spec.forkOptions(forkOptions -> {
				forkOptions.setMaxHeapSize("4096m");
			});
		});
	}

	public interface DecompileParams extends WorkParameters {
		Property<String> getDecompilerClass();

		RegularFileProperty getInputJar();
		RegularFileProperty getRuntimeJar();
		RegularFileProperty getSourcesDestinationJar();
		RegularFileProperty getLinemap();
		RegularFileProperty getLinemapJar();
		RegularFileProperty getMappings();

		RegularFileProperty getIPCPath();

		ConfigurableFileCollection getClassPath();
	}

	public abstract static class DecompileAction implements WorkAction<DecompileParams> {
		@Override
		public void execute() {
			final Path inputJar = getParameters().getInputJar().get().getAsFile().toPath();
			final Path sourcesDestinationJar = getParameters().getSourcesDestinationJar().get().getAsFile().toPath();
			final Path linemap = getParameters().getLinemap().get().getAsFile().toPath();
			final Path linemapJar = getParameters().getLinemapJar().get().getAsFile().toPath();
			final Path runtimeJar = getParameters().getRuntimeJar().get().getAsFile().toPath();

			final LoomDecompiler decompiler;

			try {
				decompiler = getDecompilerConstructor(getParameters().getDecompilerClass().get()).newInstance();
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to create decompiler", e);
			}

			try (IPCClient ipcClient = new IPCClient(getParameters().getIPCPath().get().getAsFile().toPath())) {
				DecompilationMetadata metadata = new DecompilationMetadata(
						Runtime.getRuntime().availableProcessors(),
						getParameters().getMappings().get().getAsFile().toPath(),
						getLibraries(),
						new ThreadedSimpleProgressLogger(ipcClient)
				);

				decompiler.decompile(
						inputJar,
						sourcesDestinationJar,
						linemap,
						metadata
				);

				// Close the decompile loggers
				metadata.logger().accept(ThreadedProgressLoggerConsumer.CLOSE_LOGGERS);

				if (Files.exists(linemap)) {
					try {
						// Line map the actually jar used to run the game, not the one used to decompile
						remapLineNumbers(metadata.logger(), runtimeJar, linemap, linemapJar);

						Files.copy(linemapJar, runtimeJar, StandardCopyOption.REPLACE_EXISTING);
						Files.delete(linemapJar);
					} catch (IOException e) {
						throw new UncheckedIOException("Failed to remap line numbers", e);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup IPC Client", e);
			}
		}

		private void remapLineNumbers(IOStringConsumer logger, Path oldCompiledJar, Path linemap, Path linemappedJarDestination) throws IOException {
			LineNumberRemapper remapper = new LineNumberRemapper();
			remapper.readMappings(linemap.toFile());

			try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(oldCompiledJar.toFile(), true);
					StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(linemappedJarDestination.toFile(), true)) {
				remapper.process(logger, inFs.get().getPath("/"), outFs.get().getPath("/"));
			}
		}

		private Collection<Path> getLibraries() {
			return getParameters().getClassPath().getFiles().stream().map(File::toPath).collect(Collectors.toSet());
		}
	}

	private File getMappedJarFileWithSuffix(String suffix) {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		String path = mappedJar.getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	private Path getMappings() {
		Path baseMappings = getExtension().getMappingsProvider().tinyMappings;

		if (getExtension().getEnableTransitiveAccessWideners().get()) {
			List<AccessWidenerFile> accessWideners = getExtension().getTransitiveAccessWideners();

			if (accessWideners.isEmpty()) {
				return baseMappings;
			}

			Path outputMappings;

			try {
				outputMappings = Files.createTempFile("loom-transitive-mappings", ".tiny");
			} catch (IOException e) {
				throw new RuntimeException("Failed to create temp file", e);
			}

			TransitiveAccessWidenerMappingsProcessor.process(baseMappings, outputMappings, accessWideners, getProject().getLogger());

			return outputMappings;
		}

		return baseMappings;
	}

	private static Constructor<LoomDecompiler> getDecompilerConstructor(String clazz) {
		try {
			//noinspection unchecked
			return (Constructor<LoomDecompiler>) Class.forName(clazz).getConstructor();
		} catch (NoSuchMethodException | ClassNotFoundException e) {
			return null;
		}
	}
}
