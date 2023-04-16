/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2022 FabricMC
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.slf4j.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RemapConfigurationSettings;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.task.service.LorenzMappingService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public class SourceRemapper {
	private final Project project;
	private final SharedServiceManager serviceManager;
	private final boolean toNamed;
	private final List<Consumer<ProgressLogger>> remapTasks = new ArrayList<>();

	private Mercury mercury;

	public SourceRemapper(Project project, SharedServiceManager serviceManager, boolean toNamed) {
		this.project = project;
		this.serviceManager = serviceManager;
		this.toNamed = toNamed;
	}

	public void scheduleRemapSources(File source, File destination, boolean reproducibleFileOrder, boolean preserveFileTimestamps, Runnable completionCallback) {
		remapTasks.add((logger) -> {
			try {
				logger.progress("remapping sources - " + source.getName());
				remapSourcesInner(source, destination);
				ZipReprocessorUtil.reprocessZip(destination, reproducibleFileOrder, preserveFileTimestamps);

				// Set the remapped sources creation date to match the sources if we're likely succeeded in making it
				destination.setLastModified(source.lastModified());
				completionCallback.run();
			} catch (Exception e) {
				// Failed to remap, lets clean up to ensure we try again next time
				destination.delete();
				throw new RuntimeException("Failed to remap sources for " + source, e);
			}
		});
	}

	public void remapAll() {
		if (remapTasks.isEmpty()) {
			return;
		}

		project.getLogger().lifecycle(":remapping sources");

		ProgressLoggerFactory progressLoggerFactory = ((ProjectInternal) project).getServices().get(ProgressLoggerFactory.class);
		ProgressLogger progressLogger = progressLoggerFactory.newOperation(SourceRemapper.class.getName());
		progressLogger.start("Remapping dependency sources", "sources");

		remapTasks.forEach(consumer -> consumer.accept(progressLogger));

		progressLogger.completed();

		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private void remapSourcesInner(File source, File destination) throws Exception {
		project.getLogger().info(":remapping source jar");
		Mercury mercury = getMercuryInstance();

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
			ZipUtils.unpackAll(source.toPath(), srcPath);
		}

		if (!destination.isDirectory() && destination.exists()) {
			if (!destination.delete()) {
				throw new RuntimeException("Could not delete " + destination.getName() + "!");
			}
		}

		FileSystemUtil.Delegate dstFs = destination.isDirectory() ? null : FileSystemUtil.getJarFileSystem(destination, true);
		Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination.toPath();

		try {
			mercury.rewrite(srcPath, dstPath);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + source.getName() + " fully!", e);
		}

		copyNonJavaFiles(srcPath, dstPath, project.getLogger(), source.toPath());

		if (dstFs != null) {
			dstFs.close();
		}

		if (isSrcTmp) {
			Files.walkFileTree(srcPath, new DeletingFileVisitor());
		}
	}

	private Mercury getMercuryInstance() {
		if (this.mercury != null) {
			return this.mercury;
		}

		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();

		MappingSet mappings = LorenzMappingService.create(serviceManager,
															mappingConfiguration,
															toNamed ? MappingsNamespace.INTERMEDIARY : MappingsNamespace.NAMED,
															toNamed ? MappingsNamespace.NAMED : MappingsNamespace.INTERMEDIARY
		).mappings();

		Mercury mercury = createMercuryWithClassPath(project, toNamed);
		mercury.setSourceCompatibilityFromRelease(getJavaCompileRelease(project));

		for (File file : extension.getUnmappedModCollection()) {
			Path path = file.toPath();

			if (Files.isRegularFile(path)) {
				mercury.getClassPath().add(path);
			}
		}

		for (Path intermediaryJar : extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
			mercury.getClassPath().add(intermediaryJar);
		}

		for (Path intermediaryJar : extension.getMinecraftJars(MappingsNamespace.NAMED)) {
			mercury.getClassPath().add(intermediaryJar);
		}

		Set<File> files = project.getConfigurations()
				.detachedConfiguration(project.getDependencies().create(Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS))
				.resolve();

		for (File file : files) {
			mercury.getClassPath().add(file.toPath());
		}

		mercury.getProcessors().add(MercuryRemapper.create(mappings));

		this.mercury = mercury;
		return this.mercury;
	}

	public static int getJavaCompileRelease(Project project) {
		AtomicInteger release = new AtomicInteger(-1);

		project.getTasks().withType(JavaCompile.class, javaCompile -> {
			Property<Integer> releaseProperty = javaCompile.getOptions().getRelease();

			if (!releaseProperty.isPresent()) {
				return;
			}

			int compileRelease = releaseProperty.get();
			release.set(Math.max(release.get(), compileRelease));
		});

		final int i = release.get();

		if (i < 0) {
			// Unable to find the release used to compile with, default to the current version
			return Integer.parseInt(JavaVersion.current().getMajorVersion());
		}

		return i;
	}

	public static void copyNonJavaFiles(Path from, Path to, Logger logger, Path source) throws IOException {
		Files.walk(from).forEach(path -> {
			Path targetPath = to.resolve(from.relativize(path).toString());

			if (!isJavaFile(path) && !Files.exists(targetPath)) {
				try {
					Files.copy(path, targetPath);
				} catch (IOException e) {
					logger.warn("Could not copy non-java sources '" + source + "' fully!", e);
				}
			}
		});
	}

	public static Mercury createMercuryWithClassPath(Project project, boolean toNamed) {
		Mercury m = new Mercury();
		m.setGracefulClasspathChecks(true);

		final List<Path> classPath = new ArrayList<>();

		for (File file : project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES).getFiles()) {
			classPath.add(file.toPath());
		}

		if (!toNamed) {
			for (File file : project.getConfigurations().getByName("compileClasspath").getFiles()) {
				classPath.add(file.toPath());
			}
		} else {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);

			for (RemapConfigurationSettings entry : extension.getRemapConfigurations()) {
				for (File inputFile : entry.getSourceConfiguration().get().getFiles()) {
					classPath.add(inputFile.toPath());
				}
			}
		}

		for (Path path : classPath) {
			if (Files.exists(path)) {
				m.getClassPath().add(path);
			}
		}

		return m;
	}

	private static boolean isJavaFile(Path path) {
		String name = path.getFileName().toString();
		// ".java" is not a valid java file
		return name.endsWith(".java") && name.length() != 5;
	}
}
