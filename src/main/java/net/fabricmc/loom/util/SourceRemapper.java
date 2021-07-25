/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.SourceProcessor;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.StitchUtil;

public class SourceRemapper {
	private final Project project;
	private final boolean toNamed;
	private final List<RemapTask> remapTasks = new ArrayList<>();
	private boolean hasStartedRemap = false;

	public SourceRemapper(Project project, boolean toNamed) {
		this.project = project;
		this.toNamed = toNamed;
	}

	public static void remapSources(Project project, File input, File output, boolean named, boolean reproducibleFileOrder, boolean preserveFileTimestamps) throws IOException {
		SourceRemapper sourceRemapper = new SourceRemapper(project, named);
		sourceRemapper.scheduleRemapSources(input, output, reproducibleFileOrder, preserveFileTimestamps);
		sourceRemapper.remapAll();
	}

	public void scheduleRemapSources(File source, File destination, boolean reproducibleFileOrder, boolean preserveFileTimestamps) {
		this.scheduleRemapSources(new RemapTask(source, destination, reproducibleFileOrder, preserveFileTimestamps));
	}

	public synchronized void scheduleRemapSources(RemapTask data) {
		if (hasStartedRemap) {
			throw new UnsupportedOperationException("Cannot add data after remapping has started");
		}

		this.remapTasks.add(data);
	}

	public void remapAll() throws IOException {
		hasStartedRemap = true;

		if (remapTasks.isEmpty()) {
			return;
		}

		int threads = Math.min(remapTasks.size(), Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
		ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
		ExecutorService remapExecutor = Executors.newFixedThreadPool(threads);

		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();

		{
			// We have to build the Mercury instances on the main thread as createMercuryRemapper resolves gradle stuff
			// TODO refactor this a bit to not do that.
			var mercuryQueue = new ConcurrentLinkedDeque<Mercury>();

			final var remapper = createMercuryRemapper();
			final var inputClasspath = getInputClasspath(project);

			for (int i = 0; i < threads; i++) {
				Mercury mercury = createMercuryWithClassPath(project, toNamed);
				mercury.getProcessors().add(remapper);
				mercury.getClassPath().addAll(inputClasspath);

				mercuryQueue.add(mercury);
			}

			ThreadLocal<Mercury> mercuryThreadLocal = ThreadLocal.withInitial(() -> Objects.requireNonNull(mercuryQueue.pop(), "No Mercury instances left"));

			for (RemapTask remapTask : this.remapTasks) {
				completableFutures.add(
						CompletableFuture.supplyAsync(() ->
							prepareForRemap(remapTask), ioExecutor)
							.thenApplyAsync(remapData -> doRemap(mercuryThreadLocal.get(), remapData), remapExecutor)
							.thenAcceptAsync(remapData -> completeRemap(remapData, remapTask), ioExecutor)
				);
			}
		}

		for (CompletableFuture<Void> future : completableFutures) {
			try {
				future.join();
			} catch (CompletionException e) {
				try {
					throw e.getCause();
				} catch (IOException ioe) {
					throw ioe;
				} catch (Throwable unknown) {
					throw new RuntimeException("An unknown exception occured when remapping", unknown);
				}
			}
		}

		ioExecutor.shutdown();
		remapExecutor.shutdown();

		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private RemapData prepareForRemap(RemapTask remapTask) {
		try {
			File source = remapTask.source();
			final File destination = remapTask.destination();

			if (source.equals(destination)) {
				if (source.isDirectory()) {
					throw new RuntimeException("Directories must differ!");
				}

				source = new File(destination.getAbsolutePath().substring(0, destination.getAbsolutePath().lastIndexOf('.')) + "-dev.jar");

				try {
					com.google.common.io.Files.move(destination, source);
				} catch (IOException e) {
					throw new RuntimeException("Could not rename " + destination.getName() + "!", e);
				}
			}

			Path srcPath = source.toPath();
			boolean isSrcTmp = false;

			if (!source.isDirectory()) {
				// create tmp directory
				isSrcTmp = true;
				srcPath = Files.createTempDirectory("fabric-loom-src");
				ZipUtil.unpack(source, srcPath.toFile());
			}

			if (!destination.isDirectory() && destination.exists()) {
				if (!destination.delete()) {
					throw new RuntimeException("Could not delete " + destination.getName() + "!");
				}
			}

			StitchUtil.FileSystemDelegate dstFs = remapTask.destination().isDirectory() ? null : StitchUtil.getJarFileSystem(remapTask.destination(), true);
			Path dstPath = dstFs != null ? dstFs.get().getPath("/") : remapTask.destination().toPath();

			return new RemapData(Objects.requireNonNull(srcPath, "srcPath"), Objects.requireNonNull(dstPath, "dstPath"), dstFs, isSrcTmp);
		} catch (IOException e) {
			throw new CompletionException("Failed to prepare for remap", e);
		}
	}

	private RemapData doRemap(Mercury mercury, RemapData remapData) {
		try {
			mercury.rewrite(remapData.source(), remapData.destination());
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + remapData.source().toString() + " fully!", e);
			// TODO do something more with this error? delete the dst file in complete remap?
		}

		return remapData;
	}

	private void completeRemap(RemapData remapData, RemapTask remapTask) {
		try {
			copyNonJavaFiles(remapData.source(), remapData.destination(), project, remapTask.source());

			if (remapData.dstFs() != null) {
				remapData.dstFs().close();
			}

			if (remapData.isSrcTmp()) {
				Files.walkFileTree(remapData.source(), new DeletingFileVisitor());
			}

			ZipReprocessorUtil.reprocessZip(remapTask.destination(), remapTask.reproducibleFileOrder(), remapTask.preserveFileTimestamps());

			// Set the remapped sources creation date to match the sources if we're likely succeeded in making it
			remapTask.destination().setLastModified(remapTask.source().lastModified());
		} catch (IOException e) {
			throw new CompletionException("Failed to complete remap", e);
		}
	}

	private SourceProcessor createMercuryRemapper() {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();

		MappingSet mappings = extension.getOrCreateSrcMappingCache(toNamed ? 1 : 0, () -> {
			try {
				TinyTree m = mappingsProvider.getMappings();
				project.getLogger().info(":loading " + (toNamed ? "intermediary -> named" : "named -> intermediary") + " source mappings");
				return new TinyMappingsReader(m, toNamed ? "intermediary" : "named", toNamed ? "named" : "intermediary").read();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		return MercuryRemapper.create(mappings);
	}

	private static void copyNonJavaFiles(Path from, Path to, Project project, File source) throws IOException {
		Files.walk(from).forEach(path -> {
			Path targetPath = to.resolve(from.relativize(path).toString());

			if (!isJavaFile(path) && !Files.exists(targetPath)) {
				try {
					Files.copy(path, targetPath);
				} catch (IOException e) {
					project.getLogger().warn("Could not copy non-java sources '" + source.getName() + "' fully!", e);
				}
			}
		});
	}

	public static Mercury createMercuryWithClassPath(Project project, boolean toNamed) {
		var mercury = new Mercury();
		mercury.setGracefulClasspathChecks(true);

		var classpath = mercury.getClassPath();
		classpath.addAll(getCompileClasspath(project, toNamed));

		return mercury;
	}

	private static Set<Path> getCompileClasspath(Project project, boolean toNamed) {
		var classpath = new HashSet<Path>();

		for (File file : project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES).getFiles()) {
			classpath.add(file.toPath());
		}

		if (!toNamed) {
			for (File file : project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).getFiles()) {
				classpath.add(file.toPath());
			}
		} else {
			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				for (File inputFile : project.getConfigurations().getByName(entry.sourceConfiguration()).getFiles()) {
					classpath.add(inputFile.toPath());
				}
			}
		}

		return classpath;
	}

	private static Set<Path> getInputClasspath(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		var classpath = new HashSet<Path>();

		for (File file : extension.getUnmappedModCollection()) {
			Path path = file.toPath();

			if (Files.isRegularFile(path)) {
				classpath.add(path);
			}
		}

		classpath.add(extension.getMinecraftMappedProvider().getMappedJar().toPath());
		classpath.add(extension.getMinecraftMappedProvider().getIntermediaryJar().toPath());

		Set<File> files = project.getConfigurations()
				.detachedConfiguration(project.getDependencies().create(Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS))
				.resolve();

		for (File file : files) {
			classpath.add(file.toPath());
		}

		return classpath;
	}

	private static boolean isJavaFile(Path path) {
		String name = path.getFileName().toString();
		// ".java" is not a valid java file
		return name.endsWith(".java") && name.length() != 5;
	}

	public static record RemapTask(File source, File destination, boolean reproducibleFileOrder, boolean preserveFileTimestamps) {
	}

	public static record RemapData(Path source, Path destination, StitchUtil.FileSystemDelegate dstFs, boolean isSrcTmp) {
	}
}
