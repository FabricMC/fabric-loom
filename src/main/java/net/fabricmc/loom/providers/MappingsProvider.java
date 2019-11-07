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

package net.fabricmc.loom.providers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.Version;
import net.fabricmc.mapping.reader.v2.TinyV2Factory;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.stitch.commands.tinyv2.CommandMergeTinyV2;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;

public class MappingsProvider extends DependencyProvider {
	public MinecraftMappedProvider mappedProvider;

	public String mappingsName;
	public String minecraftVersion;
	public String mappingsVersion;

	private Path mappingsDir;
	private Path mappingsStepsDir;
	// The mappings that gradle gives us
	private Path baseTinyMappings;
	// The mappings we use in practice
	public File tinyMappings;
	public File mappingsMixinExport;

	public void clean() throws IOException {
		FileUtils.deleteDirectory(mappingsDir.toFile());
	}

	public TinyTree getMappings() throws IOException {
		return MappingsCache.INSTANCE.get(tinyMappings.toPath());
	}

	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProvider minecraftProvider = getDependencyManager().getProvider(MinecraftProvider.class);

		project.getLogger().lifecycle(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		File mappingsJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find yarn mappings: " + dependency));

		this.mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");

		Version mappingsVersion = new Version(version);
		this.minecraftVersion = mappingsVersion.getMinecraftVersion();
		this.mappingsVersion = mappingsVersion.getMappingsVersion();

		initFiles(project);

		Files.createDirectories(mappingsDir);
		Files.createDirectories(mappingsStepsDir);

		tinyMappings = mappingsDir.resolve(StringUtils.removeSuffix(mappingsJar.getName(),".jar") + ".tiny").toFile();

		if (!tinyMappings.exists()) {
			storeMappings(project, minecraftProvider, mappingsJar.toPath());
		}

		mappedProvider = new MinecraftMappedProvider();
		mappedProvider.initFiles(project, minecraftProvider, this);
		mappedProvider.provide(dependency, project, extension, postPopulationScheduler);
	}

	private void storeMappings(Project project, MinecraftProvider minecraftProvider, Path yarnJar) throws IOException {
		project.getLogger().lifecycle(":extracting " + yarnJar.getFileName());

		try (FileSystem fileSystem = FileSystems.newFileSystem(yarnJar, null)) {
			extractMappings(fileSystem, baseTinyMappings);
		}

		if (baseMappingsAreV2()) {
			// These are unmerged v2 mappings

			//TODO: check that v1 and v2 doesn't clash
//			mappingsName += "-v2";
//			setMappingsLocation();

			// Download an extract intermediary
			String encodedMinecraftVersion = URLEncoder.encode(minecraftVersion, StandardCharsets.UTF_8.toString());
			String intermediaryArtifactUrl = "https://maven.fabricmc.net/net/fabricmc/intermediary/" + encodedMinecraftVersion + "/intermediary-" + encodedMinecraftVersion + "-v2.jar";
			Path intermediaryJar = mappingsStepsDir.resolve("v2-intermediary-" + minecraftVersion + ".jar");
			DownloadUtil.downloadIfChanged(new URL(intermediaryArtifactUrl), intermediaryJar.toFile(), project.getLogger());

			mergeAndSaveMappings(project, intermediaryJar, yarnJar);
		} else {
//			setMappingsLocation();

			// These are merged v1 mappings
			if (tinyMappings.exists()) {
				tinyMappings.delete();
			}

			project.getLogger().lifecycle(":populating field names");
			suggestFieldNames(minecraftProvider, baseTinyMappings, tinyMappings.toPath());
		}
	}

	private void setMappingsLocation() {
	}

	private boolean baseMappingsAreV2() throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(baseTinyMappings)) {
			TinyV2Factory.readMetadata(reader);
			return true;
		} catch (IllegalArgumentException e) {
			// TODO: just check the mappings version when Parser supports V1 in readMetadata()
			return false;
		}
	}

	private void extractMappings(FileSystem jar, Path extractTo) throws IOException {
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}

	private void mergeAndSaveMappings(Project project, Path unmergedIntermediaryJar, Path unmergedYarnJar) throws IOException {
		Path unmergedIntermediary = Paths.get(mappingsStepsDir.toString(), "unmerged-intermediary.tiny");
		project.getLogger().info(":extracting " + unmergedIntermediaryJar.getFileName());

		try (FileSystem unmergedIntermediaryFs = FileSystems.newFileSystem(unmergedIntermediaryJar, null)) {
			extractMappings(unmergedIntermediaryFs, unmergedIntermediary);
		}

		Path unmergedYarn = Paths.get(mappingsStepsDir.toString(), "unmerged-yarn.tiny");
		project.getLogger().info(":extracting " + unmergedYarnJar.getFileName());

		try (FileSystem unmergedYarnJarFs = FileSystems.newFileSystem(unmergedYarnJar, null)) {
			extractMappings(unmergedYarnJarFs, unmergedYarn);
		}

		Path invertedIntermediary = Paths.get(mappingsStepsDir.toString(), "inverted-intermediary.tiny");
		reorderMappings(unmergedIntermediary, invertedIntermediary, "intermediary", "official");
		Path unorderedMergedMappings = Paths.get(mappingsStepsDir.toString(), "unordered-merged.tiny");
		project.getLogger().info(":merging");
		mergeMappings(invertedIntermediary, unmergedYarn, unorderedMergedMappings);
		reorderMappings(unorderedMergedMappings, tinyMappings.toPath(), "official", "intermediary", "named");
	}

	private void reorderMappings(Path oldMappings, Path newMappings, String... newOrder) {
		Command command = new CommandReorderTinyV2();
		String[] args = new String[2 + newOrder.length];
		args[0] = oldMappings.toAbsolutePath().toString();
		args[1] = newMappings.toAbsolutePath().toString();
		System.arraycopy(newOrder, 0, args, 2, newOrder.length);
		runCommand(command, args);
	}

	private void mergeMappings(Path intermediaryMappings, Path yarnMappings, Path newMergedMappings) {
		try {
			Command command = new CommandMergeTinyV2();
			runCommand(command, intermediaryMappings.toAbsolutePath().toString(),
							yarnMappings.toAbsolutePath().toString(),
							newMergedMappings.toAbsolutePath().toString(),
							"intermediary", "official");
		} catch (Exception e) {
			throw new RuntimeException("Could not merge mappings from " + intermediaryMappings.toString()
							+ " with mappings from " + yarnMappings, e);
		}
	}

	private void suggestFieldNames(MinecraftProvider minecraftProvider, Path oldMappings, Path newMappings) {
		Command command = new CommandProposeFieldNames();
		runCommand(command, minecraftProvider.MINECRAFT_MERGED_JAR.getAbsolutePath(),
						oldMappings.toAbsolutePath().toString(),
						newMappings.toAbsolutePath().toString());
	}

	private void runCommand(Command command, String... args) {
		try {
			command.run(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void initFiles(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		mappingsDir = extension.getUserCache().toPath().resolve("mappings");
		mappingsStepsDir = mappingsDir.resolve("steps");

		baseTinyMappings = mappingsDir.resolve(mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion + "-base");
		mappingsMixinExport = new File(extension.getProjectBuildCache(), "mixin-map-" + minecraftVersion + "-" + mappingsVersion + ".tiny");
	}

	@Override
	public String getTargetConfig() {
		return Constants.MAPPINGS;
	}

	@Nullable
	public TinyTree getMappingsOfVersion(Project project, String version) throws IOException {
		Path migrateMappingsDir = mappingsDir.resolve("migrate");
		Path localMappingsOfVersion = migrateMappingsDir.resolve(version + ".tiny");

		if (Files.exists(localMappingsOfVersion)) {
			try (BufferedReader reader = Files.newBufferedReader(localMappingsOfVersion)) {
				return TinyMappingFactory.loadWithDetection(reader);
			}
		} else {
			//TODO: download unmerged v2 mappings once they are on the maven (v2-yarn)
			//TODO: check this still works
			String versionRelativePath = URLEncoder.encode(version, StandardCharsets.UTF_8.toString());
			String artifactUrl = "https://maven.fabricmc.net/net/fabricmc/yarn/" + versionRelativePath + "/yarn-" + versionRelativePath + ".jar";
			File mappingsJar = File.createTempFile("migrateMappingsJar", ".jar");
			project.getLogger().lifecycle(":downloading new mappings from " + artifactUrl);
			FileUtils.copyURLToFile(new URL(artifactUrl), mappingsJar);

			try (FileSystem jar = FileSystems.newFileSystem(mappingsJar.toPath(), null)) {
				if (!Files.exists(migrateMappingsDir)) Files.createDirectory(migrateMappingsDir);
				extractMappings(jar, localMappingsOfVersion);

				try (BufferedReader reader = Files.newBufferedReader(localMappingsOfVersion)) {
					return TinyMappingFactory.loadWithDetection(reader);
				}
			}
		}
	}
}
