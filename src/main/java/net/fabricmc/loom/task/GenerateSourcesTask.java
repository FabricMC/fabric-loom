/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
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
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerDaemonClientsManager;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContextImpl;
import net.fabricmc.loom.configuration.processors.MappingProcessorContextImpl;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.AbstractMappedMinecraftProvider;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.IOStringConsumer;
import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;
import net.fabricmc.loom.util.gradle.ThreadedProgressLoggerConsumer;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;
import net.fabricmc.loom.util.gradle.WorkerDaemonClientsManagerHelper;
import net.fabricmc.loom.util.ipc.IPCClient;
import net.fabricmc.loom.util.ipc.IPCServer;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

@DisableCachingByDefault
public abstract class GenerateSourcesTask extends AbstractLoomTask {
	private final DecompilerOptions decompilerOptions;

	/**
	 * The jar name to decompile, {@link MinecraftJar#getName()}.
	 */
	@Input
	public abstract Property<String> getInputJarName();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@OutputFile
	public abstract RegularFileProperty getOutputJar();

	// Unpick
	@InputFile
	@Optional
	public abstract RegularFileProperty getUnpickDefinitions();

	@InputFiles
	@Optional
	public abstract ConfigurableFileCollection getUnpickConstantJar();

	@InputFiles
	@Optional
	public abstract ConfigurableFileCollection getUnpickClasspath();

	@OutputFile
	@Optional
	public abstract RegularFileProperty getUnpickOutputJar();

	// Injects
	@Inject
	public abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public abstract ExecOperations getExecOperations();

	@Inject
	public abstract WorkerDaemonClientsManager getWorkerDaemonClientsManager();

	// Prevent Gradle from running two gen sources tasks in parallel
	@ServiceReference(SyncTaskBuildService.NAME)
	abstract Property<SyncTaskBuildService> getSyncTask();

	@Inject
	public GenerateSourcesTask(DecompilerOptions decompilerOptions) {
		this.decompilerOptions = decompilerOptions;

		getOutputs().upToDateWhen((o) -> false);
		getClasspath().from(decompilerOptions.getClasspath()).finalizeValueOnRead();
		dependsOn(decompilerOptions.getClasspath().getBuiltBy());
	}

	@TaskAction
	public void run() throws IOException {
		final Platform platform = Platform.CURRENT;

		if (!platform.getArchitecture().is64Bit()) {
			throw new UnsupportedOperationException("GenSources task requires a 64bit JVM to run due to the memory requirements.");
		}

		final MinecraftJar minecraftJar = rebuildInputJar();
		// Input jar is the jar to decompile, this may be unpicked.
		Path inputJar = minecraftJar.getPath();
		// Runtime jar is the jar used to run the game
		final Path runtimeJar = inputJar;

		if (getUnpickDefinitions().isPresent()) {
			inputJar = unpickJar(inputJar);
		}

		if (!platform.supportsUnixDomainSockets()) {
			getProject().getLogger().warn("Decompile worker logging disabled as Unix Domain Sockets is not supported on your operating system.");

			doWork(null, inputJar, runtimeJar);
			return;
		}

		// Set up the IPC path to get the log output back from the forked JVM
		final Path ipcPath = Files.createTempFile("loom", "ipc");
		Files.deleteIfExists(ipcPath);

		try (ThreadedProgressLoggerConsumer loggerConsumer = new ThreadedProgressLoggerConsumer(getProject(), decompilerOptions.getName(), "Decompiling minecraft sources");
				IPCServer logReceiver = new IPCServer(ipcPath, loggerConsumer)) {
			doWork(logReceiver, inputJar, runtimeJar);
		} catch (InterruptedException e) {
			throw new RuntimeException("Failed to shutdown log receiver", e);
		} finally {
			Files.deleteIfExists(ipcPath);
		}
	}

	// Re-run the named minecraft provider to give us a fresh jar to decompile.
	// This prevents re-applying line maps on an existing jar.
	private MinecraftJar rebuildInputJar() {
		final List<MinecraftJar> minecraftJars;

		try (var serviceManager = new ScopedSharedServiceManager()) {
			final var configContext = new ConfigContextImpl(getProject(), serviceManager, getExtension());
			final var provideContext = new AbstractMappedMinecraftProvider.ProvideContext(false, true, configContext);
			minecraftJars = getExtension().getNamedMinecraftProvider().provide(provideContext);
		} catch (Exception e) {
			throw new RuntimeException("Failed to rebuild input jars", e);
		}

		for (MinecraftJar minecraftJar : minecraftJars) {
			if (minecraftJar.getName().equals(getInputJarName().get())) {
				return minecraftJar;
			}
		}

		throw new IllegalStateException("Could not find minecraft jar (%s) but got (%s)".formatted(
				getInputJarName().get(),
				minecraftJars.stream().map(MinecraftJar::getName).collect(Collectors.joining(", ")))
		);
	}

	private Path unpickJar(Path inputJar) {
		final Path outputJar = getUnpickOutputJar().get().getAsFile().toPath();
		final List<String> args = getUnpickArgs(inputJar, outputJar);

		ExecResult result = getExecOperations().javaexec(spec -> {
			spec.getMainClass().set("daomephsta.unpick.cli.Main");
			spec.classpath(getProject().getConfigurations().getByName(Constants.Configurations.UNPICK_CLASSPATH));
			spec.args(args);
			spec.systemProperty("java.util.logging.config.file", writeUnpickLogConfig().getAbsolutePath());
		});

		result.rethrowFailure();

		return outputJar;
	}

	private List<String> getUnpickArgs(Path inputJar, Path outputJar) {
		var fileArgs = new ArrayList<File>();

		fileArgs.add(inputJar.toFile());
		fileArgs.add(outputJar.toFile());
		fileArgs.add(getUnpickDefinitions().get().getAsFile());
		fileArgs.add(getUnpickConstantJar().getSingleFile());

		// Classpath
		for (Path minecraftJar : getExtension().getMinecraftJars(MappingsNamespace.NAMED)) {
			fileArgs.add(minecraftJar.toFile());
		}

		for (File file : getUnpickClasspath()) {
			fileArgs.add(file);
		}

		return fileArgs.stream()
				.map(File::getAbsolutePath)
				.toList();
	}

	private File writeUnpickLogConfig() {
		final File unpickLoggingConfigFile = getExtension().getFiles().getUnpickLoggingConfigFile();

		try (InputStream is = GenerateSourcesTask.class.getClassLoader().getResourceAsStream("unpick-logging.properties")) {
			Files.deleteIfExists(unpickLoggingConfigFile.toPath());
			Files.copy(Objects.requireNonNull(is), unpickLoggingConfigFile.toPath());
		} catch (IOException e) {
			throw new org.gradle.api.UncheckedIOException("Failed to copy unpick logging config", e);
		}

		return unpickLoggingConfigFile;
	}

	private void doWork(@Nullable IPCServer ipcServer, Path inputJar, Path runtimeJar) {
		final String jvmMarkerValue = UUID.randomUUID().toString();
		final WorkQueue workQueue = createWorkQueue(jvmMarkerValue);

		workQueue.submit(DecompileAction.class, params -> {
			params.getDecompilerOptions().set(decompilerOptions.toDto());

			params.getInputJar().set(inputJar.toFile());
			params.getRuntimeJar().set(runtimeJar.toFile());
			params.getSourcesDestinationJar().set(getOutputJar());
			params.getLinemap().set(getMappedJarFileWithSuffix("-sources.lmap", runtimeJar));
			params.getLinemapJar().set(getMappedJarFileWithSuffix("-linemapped.jar", runtimeJar));
			params.getMappings().set(getMappings().toFile());

			if (ipcServer != null) {
				params.getIPCPath().set(ipcServer.getPath().toFile());
			}

			params.getClassPath().setFrom(getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
		});

		try {
			workQueue.await();
		} finally {
			if (ipcServer != null) {
				boolean stopped = WorkerDaemonClientsManagerHelper.stopIdleJVM(getWorkerDaemonClientsManager(), jvmMarkerValue);

				if (!stopped && ipcServer.hasReceivedMessage()) {
					throw new RuntimeException("Failed to stop decompile worker JVM");
				}
			}
		}
	}

	private WorkQueue createWorkQueue(String jvmMarkerValue) {
		if (!useProcessIsolation()) {
			return getWorkerExecutor().classLoaderIsolation(spec -> {
				spec.getClasspath().from(getClasspath());
			});
		}

		return getWorkerExecutor().processIsolation(spec -> {
			spec.forkOptions(forkOptions -> {
				forkOptions.setMinHeapSize(String.format(Locale.ENGLISH, "%dm", Math.min(512, decompilerOptions.getMemory().get())));
				forkOptions.setMaxHeapSize(String.format(Locale.ENGLISH, "%dm", decompilerOptions.getMemory().get()));
				forkOptions.systemProperty(WorkerDaemonClientsManagerHelper.MARKER_PROP, jvmMarkerValue);
			});
			spec.getClasspath().from(getClasspath());
		});
	}

	private boolean useProcessIsolation() {
		// Useful if you want to debug the decompiler, make sure you run gradle with enough memory.
		return !Boolean.getBoolean("fabric.loom.genSources.debug");
	}

	public interface DecompileParams extends WorkParameters {
		Property<DecompilerOptions.Dto> getDecompilerOptions();

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
			if (!getParameters().getIPCPath().isPresent() || !Platform.CURRENT.supportsUnixDomainSockets()) {
				// Does not support unix domain sockets, print to sout.
				doDecompile(System.out::println);
				return;
			}

			final Path ipcPath = getParameters().getIPCPath().get().getAsFile().toPath();

			try (IPCClient ipcClient = new IPCClient(ipcPath)) {
				doDecompile(new ThreadedSimpleProgressLogger(ipcClient));
			} catch (Exception e) {
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to decompile", e);
			}
		}

		private void doDecompile(IOStringConsumer logger) {
			final Path inputJar = getParameters().getInputJar().get().getAsFile().toPath();
			final Path sourcesDestinationJar = getParameters().getSourcesDestinationJar().get().getAsFile().toPath();
			final Path linemap = getParameters().getLinemap().get().getAsFile().toPath();
			final Path linemapJar = getParameters().getLinemapJar().get().getAsFile().toPath();
			final Path runtimeJar = getParameters().getRuntimeJar().get().getAsFile().toPath();

			final DecompilerOptions.Dto decompilerOptions = getParameters().getDecompilerOptions().get();

			final LoomDecompiler decompiler;

			try {
				final String className = decompilerOptions.className();
				final Constructor<LoomDecompiler> decompilerConstructor = getDecompilerConstructor(className);
				Objects.requireNonNull(decompilerConstructor, "%s must have a no args constructor".formatted(className));

				decompiler = decompilerConstructor.newInstance();
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to create decompiler", e);
			}

			DecompilationMetadata metadata = new DecompilationMetadata(
					decompilerOptions.maxThreads(),
					getParameters().getMappings().get().getAsFile().toPath(),
					getLibraries(),
					logger,
					decompilerOptions.options()
			);

			decompiler.decompile(
					inputJar,
					sourcesDestinationJar,
					linemap,
					metadata
			);

			// Close the decompile loggers
			try {
				metadata.logger().accept(ThreadedProgressLoggerConsumer.CLOSE_LOGGERS);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to close loggers", e);
			}

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
		}

		private void remapLineNumbers(IOStringConsumer logger, Path oldCompiledJar, Path linemap, Path linemappedJarDestination) throws IOException {
			LineNumberRemapper remapper = new LineNumberRemapper();
			remapper.readMappings(linemap.toFile());

			try (FileSystemUtil.Delegate inFs = FileSystemUtil.getJarFileSystem(oldCompiledJar.toFile(), true);
					FileSystemUtil.Delegate outFs = FileSystemUtil.getJarFileSystem(linemappedJarDestination.toFile(), true)) {
				remapper.process(logger, inFs.get().getPath("/"), outFs.get().getPath("/"));
			}
		}

		private Collection<Path> getLibraries() {
			return getParameters().getClassPath().getFiles().stream().map(File::toPath).collect(Collectors.toSet());
		}
	}

	public static File getMappedJarFileWithSuffix(String suffix, Path runtimeJar) {
		final String path = runtimeJar.toFile().getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	private Path getMappings() {
		Path inputMappings = getExtension().getMappingConfiguration().tinyMappings;

		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try (Reader reader = Files.newBufferedReader(inputMappings, StandardCharsets.UTF_8)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}

		final List<MappingsProcessor> mappingsProcessors = new ArrayList<>();

		MinecraftJarProcessorManager minecraftJarProcessorManager = MinecraftJarProcessorManager.create(getProject());

		if (minecraftJarProcessorManager != null) {
			mappingsProcessors.add(mappings -> {
				try (var serviceManager = new ScopedSharedServiceManager()) {
					final var configContext = new ConfigContextImpl(getProject(), serviceManager, getExtension());
					return minecraftJarProcessorManager.processMappings(mappings, new MappingProcessorContextImpl(configContext));
				}
			});
		}

		if (mappingsProcessors.isEmpty()) {
			return inputMappings;
		}

		boolean transformed = false;

		for (MappingsProcessor mappingsProcessor : mappingsProcessors) {
			if (mappingsProcessor.transform(mappingTree)) {
				transformed = true;
			}
		}

		if (!transformed) {
			return inputMappings;
		}

		final Path outputMappings;

		try {
			outputMappings = Files.createTempFile("loom-transitive-mappings", ".tiny");
		} catch (IOException e) {
			throw new RuntimeException("Failed to create temp file", e);
		}

		try (Writer writer = Files.newBufferedWriter(outputMappings, StandardCharsets.UTF_8)) {
			Tiny2Writer tiny2Writer = new Tiny2Writer(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, MappingsNamespace.NAMED.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to write mappings", e);
		}

		return outputMappings;
	}

	public interface MappingsProcessor {
		boolean transform(MemoryMappingTree mappings);
	}

	private static Constructor<LoomDecompiler> getDecompilerConstructor(String clazz) {
		try {
			//noinspection unchecked
			return (Constructor<LoomDecompiler>) Class.forName(clazz).getConstructor();
		} catch (NoSuchMethodException e) {
			return null;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
