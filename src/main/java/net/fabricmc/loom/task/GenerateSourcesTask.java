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

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerDaemonClientsManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContextImpl;
import net.fabricmc.loom.configuration.processors.MappingProcessorContextImpl;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.AbstractMappedMinecraftProvider;
import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.decompilers.cache.CachedData;
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl;
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor;
import net.fabricmc.loom.util.Checksum;
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
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

@DisableCachingByDefault
public abstract class GenerateSourcesTask extends AbstractLoomTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenerateSourcesTask.class);
	private static final String CACHE_VERSION = "v1";
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

	@InputFiles
	@Optional
	@ApiStatus.Internal
	public abstract ConfigurableFileCollection getUnpickRuntimeClasspath();

	@OutputFile
	@Optional
	public abstract RegularFileProperty getUnpickOutputJar();

	@Input
	@Option(option = "use-cache", description = "Use the decompile cache")
	@ApiStatus.Experimental
	public abstract Property<Boolean> getUseCache();

	@Input
	@Option(option = "reset-cache", description = "When set the cache will be reset")
	@ApiStatus.Experimental
	public abstract Property<Boolean> getResetCache();

	// Internal outputs
	@ApiStatus.Internal
	@Internal
	protected abstract RegularFileProperty getDecompileCacheFile();

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

		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		getDecompileCacheFile().set(extension.getFiles().getDecompileCache(CACHE_VERSION));
		getUnpickRuntimeClasspath().from(getProject().getConfigurations().getByName(Constants.Configurations.UNPICK_CLASSPATH));

		getUseCache().convention(true);
		getResetCache().convention(extension.refreshDeps());
	}

	@TaskAction
	public void run() throws IOException {
		final Platform platform = Platform.CURRENT;

		if (!platform.getArchitecture().is64Bit()) {
			throw new UnsupportedOperationException("GenSources task requires a 64bit JVM to run due to the memory requirements.");
		}

		if (!getUseCache().get()) {
			try (var timer = new Timer("Decompiled sources")) {
				runWithoutCache();
			} catch (Exception e) {
				ExceptionUtil.processException(e, getProject());
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to decompile", e);
			}

			return;
		}

		LOGGER.info("Using decompile cache.");

		try (var timer = new Timer("Decompiled sources with cache")) {
			final Path cacheFile = getDecompileCacheFile().getAsFile().get().toPath();

			if (getResetCache().get()) {
				LOGGER.warn("Resetting decompile cache");
				Files.deleteIfExists(cacheFile);
			}

			// TODO ensure we have a lock on this file to prevent multiple tasks from running at the same time
			// TODO handle being unable to read the cache file
			Files.createDirectories(cacheFile.getParent());

			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(cacheFile, true)) {
				runWithCache(fs.getRoot());
			}
		} catch (Exception e) {
			ExceptionUtil.processException(e, getProject());
			throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to decompile", e);
		}
	}

	private void runWithCache(Path cacheRoot) throws IOException {
		final MinecraftJar minecraftJar = rebuildInputJar();
		final var cacheRules = new CachedFileStoreImpl.CacheRules(50_000, Duration.ofDays(90));
		final var decompileCache = new CachedFileStoreImpl<>(cacheRoot, CachedData.SERIALIZER, cacheRules);
		final String cacheKey = getCacheKey();
		final CachedJarProcessor cachedJarProcessor = new CachedJarProcessor(decompileCache, cacheKey);
		final CachedJarProcessor.WorkRequest workRequest;

		LOGGER.info("Decompile cache key: {}", cacheKey);

		try (var timer = new Timer("Prepare job")) {
			workRequest = cachedJarProcessor.prepareJob(minecraftJar.getPath());
		}

		final CachedJarProcessor.WorkJob job = workRequest.job();
		final CachedJarProcessor.CacheStats cacheStats = workRequest.stats();

		getProject().getLogger().lifecycle("Decompile cache stats: {} hits, {} misses", cacheStats.hits(), cacheStats.misses());

		ClassLineNumbers outputLineNumbers = null;

		if (job instanceof CachedJarProcessor.WorkToDoJob workToDoJob) {
			Path inputJar = workToDoJob.incomplete();
			@Nullable Path existingClasses = (job instanceof CachedJarProcessor.PartialWorkJob partialWorkJob) ? partialWorkJob.existingClasses() : null;

			if (getUnpickDefinitions().isPresent()) {
				try (var timer = new Timer("Unpick")) {
					inputJar = unpickJar(inputJar, existingClasses);
				}
			}

			try (var timer = new Timer("Decompile")) {
				outputLineNumbers = runDecompileJob(inputJar, workToDoJob.output(), existingClasses);
			}

			if (Files.notExists(workToDoJob.output())) {
				throw new RuntimeException("Failed to decompile sources");
			}
		} else if (job instanceof CachedJarProcessor.CompletedWorkJob completedWorkJob) {
			// Nothing to do :)
		}

		// The final output sources jar
		final Path sourcesJar = getOutputJar().get().getAsFile().toPath();
		Files.deleteIfExists(sourcesJar);

		try (var timer = new Timer("Complete job")) {
			cachedJarProcessor.completeJob(sourcesJar, job, outputLineNumbers);
		}

		LOGGER.info("Decompiled sources written to {}", sourcesJar);

		// This is the minecraft jar used at runtime.
		final Path classesJar = minecraftJar.getPath();

		// Remap the line numbers with the new and existing numbers
		final ClassLineNumbers existingLinenumbers = workRequest.lineNumbers();
		final ClassLineNumbers lineNumbers = ClassLineNumbers.merge(existingLinenumbers, outputLineNumbers);

		if (lineNumbers == null) {
			LOGGER.info("No line numbers to remap, skipping remapping");
			return;
		}

		Path tempJar = Files.createTempFile("loom", "linenumber-remap.jar");
		Files.delete(tempJar);

		try (var timer = new Timer("Remap line numbers")) {
			remapLineNumbers(lineNumbers, classesJar, tempJar);
		}

		Files.move(tempJar, classesJar, StandardCopyOption.REPLACE_EXISTING);

		try (var timer = new Timer("Prune cache")) {
			decompileCache.prune();
		}
	}

	private void runWithoutCache() throws IOException {
		final MinecraftJar minecraftJar = rebuildInputJar();

		Path inputJar = minecraftJar.getPath();
		// The final output sources jar
		final Path sourcesJar = getOutputJar().get().getAsFile().toPath();

		if (getUnpickDefinitions().isPresent()) {
			try (var timer = new Timer("Unpick")) {
				inputJar = unpickJar(inputJar, null);
			}
		}

		ClassLineNumbers lineNumbers;

		try (var timer = new Timer("Decompile")) {
			lineNumbers = runDecompileJob(inputJar, sourcesJar, null);
		}

		if (Files.notExists(sourcesJar)) {
			throw new RuntimeException("Failed to decompile sources");
		}

		LOGGER.info("Decompiled sources written to {}", sourcesJar);

		if (lineNumbers == null) {
			LOGGER.info("No line numbers to remap, skipping remapping");
			return;
		}

		// This is the minecraft jar used at runtime.
		final Path classesJar = minecraftJar.getPath();
		final Path tempJar = Files.createTempFile("loom", "linenumber-remap.jar");
		Files.delete(tempJar);

		try (var timer = new Timer("Remap line numbers")) {
			remapLineNumbers(lineNumbers, classesJar, tempJar);
		}

		Files.move(tempJar, classesJar, StandardCopyOption.REPLACE_EXISTING);
	}

	private String getCacheKey() {
		var sj = new StringJoiner(",");
		sj.add(getDecompilerCheckKey());
		sj.add(getUnpickCacheKey());

		LOGGER.info("Decompile cache data: {}", sj);

		try {
			return Checksum.sha256Hex(sj.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private String getDecompilerCheckKey() {
		var sj = new StringJoiner(",");
		sj.add(decompilerOptions.getDecompilerClassName().get());
		sj.add(fileCollectionHash(decompilerOptions.getClasspath()));

		for (Map.Entry<String, String> entry : decompilerOptions.getOptions().get().entrySet()) {
			sj.add(entry.getKey() + "=" + entry.getValue());
		}

		return sj.toString();
	}

	private String getUnpickCacheKey() {
		if (!getUnpickDefinitions().isPresent()) {
			return "";
		}

		var sj = new StringJoiner(",");
		sj.add(fileHash(getUnpickDefinitions().getAsFile().get()));
		sj.add(fileCollectionHash(getUnpickConstantJar()));
		sj.add(fileCollectionHash(getUnpickRuntimeClasspath()));

		return sj.toString();
	}

	@Nullable
	private ClassLineNumbers runDecompileJob(Path inputJar, Path outputJar, @Nullable Path existingJar) throws IOException {
		final Platform platform = Platform.CURRENT;
		final Path lineMapFile = File.createTempFile("loom", "linemap").toPath();
		Files.delete(lineMapFile);

		if (!platform.supportsUnixDomainSockets()) {
			getProject().getLogger().warn("Decompile worker logging disabled as Unix Domain Sockets is not supported on your operating system.");

			doWork(null, inputJar, outputJar, lineMapFile, existingJar);
			return readLineNumbers(lineMapFile);
		}

		// Set up the IPC path to get the log output back from the forked JVM
		final Path ipcPath = Files.createTempFile("loom", "ipc");
		Files.deleteIfExists(ipcPath);

		try (ThreadedProgressLoggerConsumer loggerConsumer = new ThreadedProgressLoggerConsumer(getProject(), decompilerOptions.getName(), "Decompiling minecraft sources");
				IPCServer logReceiver = new IPCServer(ipcPath, loggerConsumer)) {
			doWork(logReceiver, inputJar, outputJar, lineMapFile, existingJar);
		} catch (InterruptedException e) {
			throw new RuntimeException("Failed to shutdown log receiver", e);
		} finally {
			Files.deleteIfExists(ipcPath);
		}

		return readLineNumbers(lineMapFile);
	}

	// Re-run the named minecraft provider to give us a fresh jar to decompile.
	// This prevents re-applying line maps on an existing jar.
	private MinecraftJar rebuildInputJar() {
		final List<MinecraftJar> minecraftJars;

		try (var serviceFactory = new ScopedServiceFactory()) {
			final var configContext = new ConfigContextImpl(getProject(), serviceFactory, getExtension());
			final var provideContext = new AbstractMappedMinecraftProvider.ProvideContext(false, true, configContext);
			minecraftJars = getExtension().getNamedMinecraftProvider().provide(provideContext);
		} catch (Exception e) {
			throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to rebuild input jars", e);
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

	private Path unpickJar(Path inputJar, @Nullable Path existingClasses) {
		final Path outputJar = getUnpickOutputJar().get().getAsFile().toPath();
		final List<String> args = getUnpickArgs(inputJar, outputJar, existingClasses);

		ExecResult result = getExecOperations().javaexec(spec -> {
			spec.getMainClass().set("daomephsta.unpick.cli.Main");
			spec.classpath(getUnpickRuntimeClasspath());
			spec.args(args);
			spec.systemProperty("java.util.logging.config.file", writeUnpickLogConfig().getAbsolutePath());
		});

		result.rethrowFailure();

		return outputJar;
	}

	private List<String> getUnpickArgs(Path inputJar, Path outputJar, @Nullable Path existingClasses) {
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

		if (existingClasses != null) {
			fileArgs.add(existingClasses.toFile());
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

	private void remapLineNumbers(ClassLineNumbers lineNumbers, Path inputJar, Path outputJar) throws IOException {
		Objects.requireNonNull(lineNumbers, "lineNumbers");
		final var remapper = new LineNumberRemapper(lineNumbers);
		remapper.process(inputJar, outputJar);

		final Path lineMap = inputJar.resolveSibling(inputJar.getFileName() + ".linemap.txt");

		try (BufferedWriter writer = Files.newBufferedWriter(lineMap)) {
			lineNumbers.write(writer);
		}

		LOGGER.info("Wrote linemap to {}", lineMap);
	}

	private void doWork(@Nullable IPCServer ipcServer, Path inputJar, Path outputJar, Path linemapFile, @Nullable Path existingClasses) {
		final String jvmMarkerValue = UUID.randomUUID().toString();
		final WorkQueue workQueue = createWorkQueue(jvmMarkerValue);

		ConfigurableFileCollection classpath = getProject().files();
		classpath.from(getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));

		if (existingClasses != null) {
			classpath.from(existingClasses);
		}

		workQueue.submit(DecompileAction.class, params -> {
			params.getDecompilerOptions().set(decompilerOptions.toDto());

			params.getInputJar().set(inputJar.toFile());
			params.getOutputJar().set(outputJar.toFile());
			params.getLinemapFile().set(linemapFile.toFile());
			params.getMappings().set(getMappings().toFile());

			if (ipcServer != null) {
				params.getIPCPath().set(ipcServer.getPath().toFile());
			}

			params.getClassPath().setFrom(classpath);
		});

		try {
			workQueue.await();
		} finally {
			if (ipcServer != null) {
				boolean stopped = WorkerDaemonClientsManagerHelper.stopIdleJVM(getWorkerDaemonClientsManager(), jvmMarkerValue);

				if (!stopped && ipcServer.hasReceivedMessage()) {
					LOGGER.info("Failed to stop decompile worker JVM, it may have already been stopped?");
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
		RegularFileProperty getOutputJar();
		RegularFileProperty getLinemapFile();
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
			final Path linemap = getParameters().getLinemapFile().get().getAsFile().toPath();
			final Path outputJar = getParameters().getOutputJar().get().getAsFile().toPath();

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

			final var metadata = new DecompilationMetadata(
					decompilerOptions.maxThreads(),
					getParameters().getMappings().get().getAsFile().toPath(),
					getLibraries(),
					logger,
					decompilerOptions.options()
			);

			decompiler.decompile(
					inputJar,
					outputJar,
					linemap,
					metadata
			);

			// Close the decompile loggers
			try {
				metadata.logger().accept(ThreadedProgressLoggerConsumer.CLOSE_LOGGERS);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to close loggers", e);
			}
		}

		private Collection<Path> getLibraries() {
			return getParameters().getClassPath().getFiles().stream().map(File::toPath).collect(Collectors.toSet());
		}
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
				try (var serviceFactory = new ScopedServiceFactory()) {
					final var configContext = new ConfigContextImpl(getProject(), serviceFactory, getExtension());
					return minecraftJarProcessorManager.processMappings(mappings, new MappingProcessorContextImpl(configContext));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
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
			var tiny2Writer = new Tiny2FileWriter(writer, false);
			mappingTree.accept(new MappingSourceNsSwitch(tiny2Writer, MappingsNamespace.NAMED.toString()));
		} catch (IOException e) {
			throw new RuntimeException("Failed to write mappings", e);
		}

		return outputMappings;
	}

	public static File getJarFileWithSuffix(String suffix, Path runtimeJar) {
		final String path = runtimeJar.toFile().getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	@Nullable
	private static ClassLineNumbers readLineNumbers(Path linemapFile) throws IOException {
		if (Files.notExists(linemapFile)) {
			return null;
		}

		try (BufferedReader reader = Files.newBufferedReader(linemapFile, StandardCharsets.UTF_8)) {
			return ClassLineNumbers.readMappings(reader);
		}
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

	private static String fileHash(File file) {
		try {
			return Checksum.sha256Hex(Files.readAllBytes(file.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String fileCollectionHash(FileCollection files) {
		var sj = new StringJoiner(",");

		files.getFiles()
				.stream()
				.sorted(Comparator.comparing(File::getAbsolutePath))
				.map(GenerateSourcesTask::fileHash)
				.forEach(sj::add);

		return sj.toString();
	}

	public interface MappingsProcessor {
		boolean transform(MemoryMappingTree mappings);
	}

	private final class Timer implements AutoCloseable {
		private final String name;
		private final long start;

		Timer(String name) {
			this.name = name;
			this.start = System.currentTimeMillis();
		}

		@Override
		public void close() {
			getProject().getLogger().info("{} took {}ms", name, System.currentTimeMillis() - start);
		}
	}
}
