/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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
import java.util.function.Consumer;

import com.google.common.base.Stopwatch;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.providers.LaunchProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.tr.MercuryUtils;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.StitchUtil;

public class SourceRemapper {
	private final Project project;
	private String from;
	private String to;
	private final List<Consumer<Mercury>> remapTasks = new ArrayList<>();

	private Mercury mercury;

	public SourceRemapper(Project project, boolean named) {
		this(project, named ? intermediary(project) : "named", !named ? intermediary(project) : "named");
	}

	public static String intermediary(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		return extension.isForge() ? "srg" : "intermediary";
	}

	public SourceRemapper(Project project, String from, String to) {
		this.project = project;
		this.from = from;
		this.to = to;
	}

	public static void remapSources(Project project, File input, File output, String from, String to, boolean reproducibleFileOrder, boolean preserveFileTimestamps) {
		SourceRemapper sourceRemapper = new SourceRemapper(project, from, to);
		sourceRemapper.scheduleRemapSources(input, output, reproducibleFileOrder, preserveFileTimestamps);
		sourceRemapper.remapAll();
	}

	public void scheduleRemapSources(File source, File destination, boolean reproducibleFileOrder, boolean preserveFileTimestamps) {
		remapTasks.add((mercury) -> {
			try {
				remapSourcesInner(mercury, source, destination);
				ZipReprocessorUtil.reprocessZip(destination, reproducibleFileOrder, preserveFileTimestamps);

				// Set the remapped sources creation date to match the sources if we're likely succeeded in making it
				destination.setLastModified(source.lastModified());
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

		Stopwatch stopwatch = Stopwatch.createStarted();
		project.getLogger().lifecycle(":remapping " + remapTasks.size() + " sources");

		Mercury mercury = getMercuryInstance();
		ThreadingUtils.run(remapTasks, consumer -> consumer.accept(MercuryUtils.copyMercury(mercury)));

		project.getLogger().lifecycle(":remapped " + remapTasks.size() + " sources in " + stopwatch.stop());

		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private void remapSourcesInner(Mercury mercury, File source, File destination) throws Exception {
		Stopwatch stopwatch = Stopwatch.createStarted();
		project.getLogger().info(":remapping source jar " + source.getName() + " from " + from + " to " + to);

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

		StitchUtil.FileSystemDelegate dstFs = destination.isDirectory() ? null : StitchUtil.getJarFileSystem(destination, true);
		Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination.toPath();

		try {
			mercury.rewrite(srcPath, dstPath);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + source.getName() + " fully!", e);
		}

		copyNonJavaFiles(srcPath, dstPath, project, source);

		if (dstFs != null) {
			dstFs.close();
		}

		if (isSrcTmp) {
			Files.walkFileTree(srcPath, new DeletingFileVisitor());
		}

		project.getLogger().info(":remapped source jar " + source.getName() + " from " + from + " to " + to + " in " + stopwatch.stop());
	}

	private Mercury getMercuryInstance() {
		if (this.mercury != null) {
			return this.mercury;
		}

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String intermediary = extension.isForge() ? "srg" : "intermediary";
		int id = -1;

		if (from.equals(intermediary) && to.equals("named")) {
			id = 1;
		} else if (to.equals(intermediary) && from.equals("named")) {
			id = 0;
		}

		MappingSet mappings = extension.getOrCreateSrcMappingCache(id, () -> {
			try {
				TinyTree m = extension.shouldGenerateSrgTiny() ? mappingsProvider.getMappingsWithSrg() : mappingsProvider.getMappings();
				project.getLogger().info(":loading " + from + " -> " + to + " source mappings");
				return new TinyMappingsReader(m, from, to).read();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		Mercury mercury = extension.getOrCreateSrcMercuryCache(id, () -> {
			Mercury m = createMercuryWithClassPath(project, to.equals("named"));

			for (File file : extension.getUnmappedModCollection()) {
				Path path = file.toPath();

				if (Files.isRegularFile(path)) {
					m.getClassPath().add(path);
				}
			}

			m.getClassPath().add(extension.getMinecraftMappedProvider().getMappedJar().toPath());
			m.getClassPath().add(extension.getMinecraftMappedProvider().getIntermediaryJar().toPath());

			if (extension.isForge()) {
				m.getClassPath().add(extension.getMinecraftMappedProvider().getSrgJar().toPath());
			}

			Dependency annotationDependency = extension.getDependencyManager().getProvider(LaunchProvider.class).annotationDependency;
			Set<File> files = project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
					.files(annotationDependency);

			for (File file : files) {
				m.getClassPath().add(file.toPath());
			}

			m.getProcessors().add(MercuryRemapper.create(mappings));

			return m;
		});

		this.mercury = mercury;
		return mercury;
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
		Mercury m = new Mercury();
		m.setGracefulClasspathChecks(true);

		for (File file : project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES).getFiles()) {
			m.getClassPath().add(file.toPath());
		}

		if (!toNamed) {
			for (File file : project.getConfigurations().getByName("compileClasspath").getFiles()) {
				m.getClassPath().add(file.toPath());
			}
		} else {
			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				for (File inputFile : project.getConfigurations().getByName(entry.getSourceConfiguration()).getFiles()) {
					m.getClassPath().add(inputFile.toPath());
				}
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
