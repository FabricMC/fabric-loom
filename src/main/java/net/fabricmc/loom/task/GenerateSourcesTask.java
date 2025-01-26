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
import java.io.UncheckedIOException;
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
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerDaemonClientsManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.AbstractMappedMinecraftProvider;
import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.decompilers.cache.CachedData;
import net.fabricmc.loom.decompilers.cache.CachedFileStoreImpl;
import net.fabricmc.loom.decompilers.cache.CachedJarProcessor;
import net.fabricmc.loom.task.service.SourceMappingsService;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.IOStringConsumer;
import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;
import net.fabricmc.loom.util.gradle.ThreadedProgressLoggerConsumer;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;
import net.fabricmc.loom.util.gradle.WorkerDaemonClientsManagerHelper;
import net.fabricmc.loom.util.gradle.daemon.DaemonUtils;
import net.fabricmc.loom.util.ipc.IPCClient;
import net.fabricmc.loom.util.ipc.IPCServer;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

@UntrackedTask(because = "Manually invoked, has internal caching")
public abstract class GenerateSourcesTask extends AbstractLoomTask {
	private static final String CACHE_VERSION = "v1";
	private final DecompilerOptions decompilerOptions;

	/**
	 * The jar name to decompile, {@link MinecraftJar#getName()}.
	 */
	@Input
	public abstract Property<String> getInputJarName();

	@InputFiles // Only contains a single file
	protected abstract ConfigurableFileCollection getClassesInputJar();

	@InputFiles
	protected abstract ConfigurableFileCollection getClasspath();

	@InputFiles
	protected abstract ConfigurableFileCollection getMinecraftCompileLibraries();

	@OutputFile
	public abstract RegularFileProperty getSourcesOutputJar();

	// Contains the remapped linenumbers
	@OutputFile
	protected abstract ConfigurableFileCollection getClassesOutputJar(); // Single jar

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

	@OutputFile
	protected abstract RegularFileProperty getUnpickLogConfig();

	@Input
	@Option(option = "use-cache", description = "Use the decompile cache")
	@ApiStatus.Experimental
	public abstract Property<Boolean> getUseCache();

	@Input
	@Option(option = "reset-cache", description = "When set the cache will be reset")
	@ApiStatus.Experimental
	public abstract Property<Boolean> getResetCache();

	// Internal inputs
	@ApiStatus.Internal
	@Nested
	protected abstract Property<SourceMappingsService.Options> getMappings();

	// Internal outputs
	@ApiStatus.Internal
	@Internal
	protected abstract RegularFileProperty getDecompileCacheFile();

	@ApiStatus.Internal
	@Input
	protected abstract Property<Integer> getMaxCachedFiles();

	@ApiStatus.Internal
	@Input
	protected abstract Property<Integer> getMaxCacheFileAge();

	// Injects
	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	protected abstract ExecOperations getExecOperations();

	@Inject
	protected abstract WorkerDaemonClientsManager getWorkerDaemonClientsManager();

	@Inject
	protected abstract ProgressLoggerFactory getProgressLoggerFactory();

	@Nested
	protected abstract Property<DaemonUtils.Context> getDaemonUtilsContext();

	// Prevent Gradle from running two gen sources tasks in parallel
	@ServiceReference(SyncTaskBuildService.NAME)
	abstract Property<SyncTaskBuildService> getSyncTask();

	@Inject
	public GenerateSourcesTask(DecompilerOptions decompilerOptions) {
		this.decompilerOptions = decompilerOptions;

		getClassesInputJar().setFrom(getInputJarName().map(minecraftJarName -> {
			final List<MinecraftJar> minecraftJars = getExtension().getNamedMinecraftProvider().getMinecraftJars();

			for (MinecraftJar minecraftJar : minecraftJars) {
				if (minecraftJar.getName().equals(minecraftJarName)) {
					final Path backupJarPath = AbstractMappedMinecraftProvider.getBackupJarPath(minecraftJar);

					if (Files.notExists(backupJarPath)) {
						throw new IllegalStateException("Input minecraft jar not found at: " + backupJarPath);
					}

					return backupJarPath.toFile();
				}
			}

			throw new IllegalStateException("Input minecraft jar not found: " + getInputJarName().get());
		}));
		getClassesOutputJar().setFrom(getInputJarName().map(minecraftJarName -> {
			final List<MinecraftJar> minecraftJars = getExtension().getNamedMinecraftProvider().getMinecraftJars();

			for (MinecraftJar minecraftJar : minecraftJars) {
				if (minecraftJar.getName().equals(minecraftJarName)) {
					return minecraftJar.toFile();
				}
			}

			throw new IllegalStateException("Input minecraft jar not found: " + getInputJarName().get());
		}));

		getClasspath().from(decompilerOptions.getClasspath()).finalizeValueOnRead();
		dependsOn(decompilerOptions.getClasspath().getBuiltBy());

		getMinecraftCompileLibraries().from(getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
		getDecompileCacheFile().set(getExtension().getFiles().getDecompileCache(CACHE_VERSION));
		getUnpickRuntimeClasspath().from(getProject().getConfigurations().getByName(Constants.Configurations.UNPICK_CLASSPATH));
		getUnpickLogConfig().set(getExtension().getFiles().getUnpickLoggingConfigFile());

		getUseCache().convention(true);
		getResetCache().convention(getExtension().refreshDeps());

		getMappings().set(SourceMappingsService.create(getProject()));

		getMaxCachedFiles().set(GradleUtils.getIntegerPropertyProvider(getProject(), Constants.Properties.DECOMPILE_CACHE_MAX_FILES).orElse(50_000));
		getMaxCacheFileAge().set(GradleUtils.getIntegerPropertyProvider(getProject(), Constants.Properties.DECOMPILE_CACHE_MAX_AGE).orElse(90));

		getDaemonUtilsContext().set(getProject().getObjects().newInstance(DaemonUtils.Context.class, getProject()));

		mustRunAfter(getProject().getTasks().withType(AbstractRemapJarTask.class));
	}

	@TaskAction
	public void run() throws IOException {
		final Platform platform = Platform.CURRENT;

		if (!platform.getArchitecture().is64Bit()) {
			throw new UnsupportedOperationException("GenSources task requires a 64bit JVM to run due to the memory requirements.");
		}

		if (!getUseCache().get()) {
			getLogger().info("Not using decompile cache.");

			try (var timer = new Timer("Decompiled sources")) {
				runWithoutCache();
			} catch (Exception e) {
				ExceptionUtil.processException(e, getDaemonUtilsContext().get());
				throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to decompile", e);
			}

			return;
		}

		getLogger().info("Using decompile cache.");

		try (var timer = new Timer("Decompiled sources with cache")) {
			final Path cacheFile = getDecompileCacheFile().getAsFile().get().toPath();

			if (getResetCache().get()) {
				getLogger().warn("Resetting decompile cache");
				Files.deleteIfExists(cacheFile);
			}

			// TODO ensure we have a lock on this file to prevent multiple tasks from running at the same time
			Files.createDirectories(cacheFile.getParent());

			if (Files.exists(cacheFile)) {
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(cacheFile, true)) {
					// Success, cache exists and can be read
				} catch (IOException e) {
					getLogger().warn("Discarding invalid decompile cache file: {}", cacheFile, e);
					Files.delete(cacheFile);
				}
			}

			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(cacheFile, true)) {
				runWithCache(fs.getRoot());
			}
		} catch (Exception e) {
			ExceptionUtil.processException(e, getDaemonUtilsContext().get());
			throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to decompile", e);
		}
	}

	private void runWithCache(Path cacheRoot) throws IOException {
		final Path classesInputJar = getClassesInputJar().getSingleFile().toPath();
		final Path sourcesOutputJar = getSourcesOutputJar().get().getAsFile().toPath();
		final Path classesOutputJar = getClassesOutputJar().getSingleFile().toPath();
		final var cacheRules = new CachedFileStoreImpl.CacheRules(getMaxCachedFiles().get(), Duration.ofDays(getMaxCacheFileAge().get()));
		final var decompileCache = new CachedFileStoreImpl<>(cacheRoot, CachedData.SERIALIZER, cacheRules);
		final String cacheKey = getCacheKey();
		final CachedJarProcessor cachedJarProcessor = new CachedJarProcessor(decompileCache, cacheKey);
		final CachedJarProcessor.WorkRequest workRequest;

		getLogger().info("Decompile cache key: {}", cacheKey);
		getLogger().debug("Decompile cache rules: {}", cacheRules);

		try (var timer = new Timer("Prepare job")) {
			workRequest = cachedJarProcessor.prepareJob(classesInputJar);
		}

		final CachedJarProcessor.WorkJob job = workRequest.job();
		final CachedJarProcessor.CacheStats cacheStats = workRequest.stats();

		getLogger().lifecycle("Decompile cache stats: {} hits, {} misses", cacheStats.hits(), cacheStats.misses());

		ClassLineNumbers outputLineNumbers = null;

		if (job instanceof CachedJarProcessor.WorkToDoJob workToDoJob) {
			Path workInputJar = workToDoJob.incomplete();
			@Nullable Path existingClasses = (job instanceof CachedJarProcessor.PartialWorkJob partialWorkJob) ? partialWorkJob.existingClasses() : null;

			if (getUnpickDefinitions().isPresent()) {
				try (var timer = new Timer("Unpick")) {
					workInputJar = unpickJar(workInputJar, existingClasses);
				}
			}

			try (var timer = new Timer("Decompile")) {
				outputLineNumbers = runDecompileJob(workInputJar, workToDoJob.output(), existingClasses);
			}

			if (Files.notExists(workToDoJob.output())) {
				throw new RuntimeException("Failed to decompile sources");
			}
		} else if (job instanceof CachedJarProcessor.CompletedWorkJob completedWorkJob) {
			// Nothing to do :)
		}

		// The final output sources jar
		Files.deleteIfExists(sourcesOutputJar);

		try (var timer = new Timer("Complete job")) {
			cachedJarProcessor.completeJob(sourcesOutputJar, job, outputLineNumbers);
		}

		getLogger().info("Decompiled sources written to {}", sourcesOutputJar);

		// Remap the line numbers with the new and existing numbers
		final ClassLineNumbers existingLinenumbers = workRequest.lineNumbers();
		final ClassLineNumbers lineNumbers = ClassLineNumbers.merge(existingLinenumbers, outputLineNumbers);

		applyLineNumbers(lineNumbers, classesInputJar, classesOutputJar);

		try (var timer = new Timer("Prune cache")) {
			decompileCache.prune();
		}
	}

	private void runWithoutCache() throws IOException {
		final Path classesInputJar = getClassesInputJar().getSingleFile().toPath();
		final Path sourcesOutputJar = getSourcesOutputJar().get().getAsFile().toPath();
		final Path classesOutputJar = getClassesOutputJar().getSingleFile().toPath();

		Path workClassesJar = classesInputJar;

		if (getUnpickDefinitions().isPresent()) {
			try (var timer = new Timer("Unpick")) {
				workClassesJar = unpickJar(workClassesJar, null);
			}
		}

		ClassLineNumbers lineNumbers;

		try (var timer = new Timer("Decompile")) {
			lineNumbers = runDecompileJob(workClassesJar, sourcesOutputJar, null);
		}

		if (Files.notExists(sourcesOutputJar)) {
			throw new RuntimeException("Failed to decompile sources");
		}

		getLogger().info("Decompiled sources written to {}", sourcesOutputJar);

		applyLineNumbers(lineNumbers, classesInputJar, classesOutputJar);
	}

	private void applyLineNumbers(@Nullable ClassLineNumbers lineNumbers, Path classesInputJar, Path classesOutputJar) throws IOException {
		if (lineNumbers == null) {
			getLogger().info("No line numbers to remap, skipping remapping");
			return;
		}

		final Path tempJar = Files.createTempFile("loom", "linenumber-remap.jar");
		Files.delete(tempJar);

		try (var timer = new Timer("Remap line numbers")) {
			remapLineNumbers(lineNumbers, classesInputJar, tempJar);
		}

		Files.move(tempJar, classesOutputJar, StandardCopyOption.REPLACE_EXISTING);
	}

	private String getCacheKey() {
		var sj = new StringJoiner(",");
		sj.add(getDecompilerCheckKey());
		sj.add(getUnpickCacheKey());

		getLogger().info("Decompile cache data: {}", sj);

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
			getLogger().warn("Decompile worker logging disabled as Unix Domain Sockets is not supported on your operating system.");

			doWork(null, inputJar, outputJar, lineMapFile, existingJar);
			return readLineNumbers(lineMapFile);
		}

		// Set up the IPC path to get the log output back from the forked JVM
		final Path ipcPath = Files.createTempFile("loom", "ipc");
		Files.deleteIfExists(ipcPath);

		try (ThreadedProgressLoggerConsumer loggerConsumer = new ThreadedProgressLoggerConsumer(getLogger(), getProgressLoggerFactory(), decompilerOptions.getName(), "Decompiling minecraft sources");
				IPCServer logReceiver = new IPCServer(ipcPath, loggerConsumer)) {
			doWork(logReceiver, inputJar, outputJar, lineMapFile, existingJar);
		} catch (InterruptedException e) {
			throw new RuntimeException("Failed to shutdown log receiver", e);
		} finally {
			Files.deleteIfExists(ipcPath);
		}

		return readLineNumbers(lineMapFile);
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
		final File unpickLoggingConfigFile = getUnpickLogConfig().getAsFile().get();

		try (InputStream is = GenerateSourcesTask.class.getClassLoader().getResourceAsStream("unpick-logging.properties")) {
			Files.copy(Objects.requireNonNull(is), unpickLoggingConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to copy unpick logging config", e);
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

		getLogger().info("Wrote linemap to {}", lineMap);
	}

	private void doWork(@Nullable IPCServer ipcServer, Path inputJar, Path outputJar, Path linemapFile, @Nullable Path existingClasses) {
		final String jvmMarkerValue = UUID.randomUUID().toString();
		final WorkQueue workQueue = createWorkQueue(jvmMarkerValue);

		workQueue.submit(DecompileAction.class, params -> {
			params.getDecompilerOptions().set(decompilerOptions.toDto());

			params.getInputJar().set(inputJar.toFile());
			params.getOutputJar().set(outputJar.toFile());
			params.getLinemapFile().set(linemapFile.toFile());
			params.getMappings().set(getMappings());

			if (ipcServer != null) {
				params.getIPCPath().set(ipcServer.getPath().toFile());
			}

			params.getClassPath().setFrom(getMinecraftCompileLibraries());

			if (existingClasses != null) {
				params.getClassPath().from(existingClasses);
			}
		});

		try {
			workQueue.await();
		} finally {
			if (ipcServer != null) {
				boolean stopped = WorkerDaemonClientsManagerHelper.stopIdleJVM(getWorkerDaemonClientsManager(), jvmMarkerValue);

				if (!stopped && ipcServer.hasReceivedMessage()) {
					getLogger().info("Failed to stop decompile worker JVM, it may have already been stopped?");
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
		Property<SourceMappingsService.Options> getMappings();

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

			try (var serviceFactory = new ScopedServiceFactory()) {
				final SourceMappingsService mappingsService = serviceFactory.get(getParameters().getMappings());

				final var metadata = new DecompilationMetadata(
						decompilerOptions.maxThreads(),
						mappingsService.getMappingsFile(),
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
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private Collection<Path> getLibraries() {
			return getParameters().getClassPath().getFiles().stream().map(File::toPath).collect(Collectors.toSet());
		}
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
			getLogger().info("{} took {}ms", name, System.currentTimeMillis() - start);
		}
	}
}
