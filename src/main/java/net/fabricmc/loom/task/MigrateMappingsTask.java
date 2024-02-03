/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2022 FabricMC
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.lorenztiny.TinyMappingsJoiner;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

@DisableCachingByDefault(because = "Always rerun this task.")
public abstract class MigrateMappingsTask extends AbstractLoomTask {
	private Path inputDir;
	private Path outputDir;
	private String mappings;

	public MigrateMappingsTask() {
		inputDir = getProject().file("src/main/java").toPath();
		outputDir = getProject().file("remappedSrc").toPath();

		// Ensure we resolve the classpath inputs before running the task.
		getCompileClasspath().from(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
	}

	@Option(option = "input", description = "Java source file directory")
	public void setInputDir(String inputDir) {
		this.inputDir = getProject().file(inputDir).toPath();
	}

	@Option(option = "output", description = "Remapped source output directory")
	public void setOutputDir(String outputDir) {
		this.outputDir = getProject().file(outputDir).toPath();
	}

	@Option(option = "mappings", description = "Target mappings")
	public void setMappings(String mappings) {
		this.mappings = mappings;
	}

	@InputFiles
	public abstract ConfigurableFileCollection getCompileClasspath();

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = getExtension();

		project.getLogger().info(":loading mappings");

		if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
			throw new IllegalArgumentException("Could not find input directory: " + inputDir.toAbsolutePath());
		}

		Files.createDirectories(outputDir);

		File mappings = loadMappings();
		MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();

		try (var serviceManager = new ScopedSharedServiceManager()) {
			MemoryMappingTree currentMappings = mappingConfiguration.getMappingsService(serviceManager).getMappingTree();
			MemoryMappingTree targetMappings = getMappings(mappings);
			migrateMappings(project, extension, inputDir, outputDir, currentMappings, targetMappings);
			project.getLogger().lifecycle(":remapped project written to " + outputDir.toAbsolutePath());
		} catch (IOException e) {
			throw new IllegalArgumentException("Error while loading mappings", e);
		}
	}

	private File loadMappings() {
		Project project = getProject();

		if (mappings == null || mappings.isEmpty()) {
			throw new IllegalArgumentException("No mappings were specified. Use --mappings=\"\" to specify target mappings");
		}

		Set<File> files;

		try {
			if (mappings.startsWith("net.minecraft:mappings:")) {
				if (!mappings.endsWith(":" + LoomGradleExtension.get(project).getMinecraftProvider().minecraftVersion())) {
					throw new UnsupportedOperationException("Migrating Mojang mappings is currently only supported for the specified minecraft version");
				}

				LayeredMappingsFactory dep = new LayeredMappingsFactory(LayeredMappingSpecBuilderImpl.buildOfficialMojangMappings());
				files = Collections.singleton(dep.resolve(getProject()).toFile());
			} else {
				Dependency dependency = project.getDependencies().create(mappings);
				files = project.getConfigurations().detachedConfiguration(dependency).resolve();
			}
		} catch (IllegalDependencyNotation ignored) {
			project.getLogger().info("Could not locate mappings, presuming V2 Yarn");

			try {
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().create(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings, "classifier", "v2"))).resolve();
			} catch (GradleException ignored2) {
				project.getLogger().info("Could not locate mappings, presuming V1 Yarn");
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().create(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings))).resolve();
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to resolve mappings", e);
		}

		if (files.isEmpty()) {
			throw new IllegalArgumentException("Mappings could not be found");
		}

		return Iterables.getOnlyElement(files);
	}

	private static MemoryMappingTree getMappings(File mappings) throws IOException {
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(mappings.toPath())) {
			MappingReader.read(delegate.fs().getPath("mappings/mappings.tiny"), mappingTree);
		}

		return mappingTree;
	}

	private static void migrateMappings(Project project, LoomGradleExtension extension,
										Path inputDir, Path outputDir, MemoryMappingTree currentMappings, MemoryMappingTree targetMappings
	) throws IOException {
		project.getLogger().info(":joining mappings");

		MappingSet mappingSet = new TinyMappingsJoiner(
				currentMappings, MappingsNamespace.NAMED.toString(),
				targetMappings, MappingsNamespace.NAMED.toString(),
				MappingsNamespace.INTERMEDIARY.toString()
		).read();

		project.getLogger().lifecycle(":remapping");
		Mercury mercury = SourceRemapper.createMercuryWithClassPath(project, false);

		final JavaVersion javaVersion = project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility();
		mercury.setSourceCompatibility(javaVersion.toString());

		for (Path intermediaryJar : extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
			mercury.getClassPath().add(intermediaryJar);
		}

		for (Path intermediaryJar : extension.getMinecraftJars(MappingsNamespace.NAMED)) {
			mercury.getClassPath().add(intermediaryJar);
		}

		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		try {
			mercury.rewrite(inputDir, outputDir);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap fully!", e);
		}

		project.getLogger().info(":cleaning file descriptors");
		System.gc();
	}
}
