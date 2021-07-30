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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.function.Consumer;

import com.google.common.base.Stopwatch;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.processors.MinecraftProcessedProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.mapping.reader.v2.TinyV2Factory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.Tiny2Reader;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.stitch.commands.tinyv2.CommandMergeTinyV2;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;

public class MappingsProviderImpl extends DependencyProvider implements MappingsProvider {
	public MinecraftMappedProvider mappedProvider;

	public String mappingsIdentifier;

	private Path mappingsWorkingDir;
	private Path intermediaryTiny;
	private boolean hasRefreshed = false;
	// The mappings that gradle gives us
	private Path baseTinyMappings;
	// The mappings we use in practice
	public Path tinyMappings;
	public Path tinyMappingsJar;
	private Path unpickDefinitions;
	private boolean hasUnpickDefinitions;
	private UnpickMetadata unpickMetadata;

	public MappingsProviderImpl(Project project) {
		super(project);
	}

	public TinyTree getMappings() throws IOException {
		return MappingsCache.INSTANCE.get(tinyMappings);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProviderImpl minecraftProvider = getDependencyManager().getProvider(MinecraftProviderImpl.class);

		getProject().getLogger().info(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		File mappingsJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find yarn mappings: " + dependency));

		String mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");
		boolean isV2 = isV2(dependency, mappingsJar);
		this.mappingsIdentifier = createMappingsIdentifier(mappingsName, version, getMappingsClassifier(dependency, isV2));

		initFiles();

		if (Files.notExists(tinyMappings) || isRefreshDeps()) {
			storeMappings(getProject(), minecraftProvider, mappingsJar.toPath());
		} else {
			try (FileSystem fileSystem = FileSystems.newFileSystem(mappingsJar.toPath(), (ClassLoader) null)) {
				extractUnpickDefinitions(fileSystem, unpickDefinitions);
			}
		}

		if (Files.notExists(tinyMappingsJar) || isRefreshDeps()) {
			ZipUtil.pack(new ZipEntrySource[] {new FileSource("mappings/mappings.tiny", tinyMappings.toFile())}, tinyMappingsJar.toFile());
		}

		if (hasUnpickDefinitions()) {
			String notation = String.format("%s:%s:%s:constants",
					dependency.getDependency().getGroup(),
					dependency.getDependency().getName(),
					dependency.getDependency().getVersion()
			);

			getProject().getDependencies().add(Constants.Configurations.MAPPING_CONSTANTS, notation);
			populateUnpickClasspath();
		}

		addDependency(tinyMappingsJar.toFile(), Constants.Configurations.MAPPINGS_FINAL);

		LoomGradleExtension extension = getExtension();

		if (extension.getAccessWidenerPath().isPresent()) {
			extension.getGameJarProcessors().add(new AccessWidenerJarProcessor(getProject()));
		}

		extension.getAccessWidenerPath().finalizeValue();
		extension.getGameJarProcessors().finalizeValue();
		JarProcessorManager processorManager = new JarProcessorManager(extension.getGameJarProcessors().get());
		extension.setJarProcessorManager(processorManager);
		processorManager.setupProcessors();

		if (processorManager.active()) {
			mappedProvider = new MinecraftProcessedProvider(getProject(), processorManager);
			getProject().getLogger().lifecycle("Using project based jar storage");
		} else {
			mappedProvider = new MinecraftMappedProvider(getProject());
		}

		mappedProvider.initFiles(minecraftProvider, this);
		mappedProvider.provide(dependency, postPopulationScheduler);
	}

	private String getMappingsClassifier(DependencyInfo dependency, boolean isV2) {
		String[] depStringSplit = dependency.getDepString().split(":");

		if (depStringSplit.length >= 4) {
			return "-" + depStringSplit[3] + (isV2 ? "-v2" : "");
		}

		return isV2 ? "-v2" : "";
	}

	private boolean isV2(DependencyInfo dependency, File mappingsJar) throws IOException {
		String minecraftVersion = getMinecraftProvider().minecraftVersion();

		// Only do this for official yarn, there isn't really a way we can get the mc version for all mappings
		if (dependency.getDependency().getGroup() != null && dependency.getDependency().getGroup().equals("net.fabricmc") && dependency.getDependency().getName().equals("yarn") && dependency.getDependency().getVersion() != null) {
			String yarnVersion = dependency.getDependency().getVersion();
			char separator = yarnVersion.contains("+build.") ? '+' : yarnVersion.contains("-") ? '-' : '.';
			String yarnMinecraftVersion = yarnVersion.substring(0, yarnVersion.lastIndexOf(separator));

			if (!yarnMinecraftVersion.equalsIgnoreCase(minecraftVersion)) {
				throw new RuntimeException(String.format("Minecraft Version (%s) does not match yarn's minecraft version (%s)", minecraftVersion, yarnMinecraftVersion));
			}

			// We can save reading the zip file + header by checking the file name
			return mappingsJar.getName().endsWith("-v2.jar");
		} else {
			return doesJarContainV2Mappings(mappingsJar.toPath());
		}
	}

	private void storeMappings(Project project, MinecraftProviderImpl minecraftProvider, Path yarnJar) throws IOException {
		project.getLogger().info(":extracting " + yarnJar.getFileName());

		try (FileSystem fileSystem = FileSystems.newFileSystem(yarnJar, (ClassLoader) null)) {
			extractMappings(fileSystem, baseTinyMappings);
			extractUnpickDefinitions(fileSystem, unpickDefinitions);
		}

		if (areMappingsV2(baseTinyMappings)) {
			// These are unmerged v2 mappings
			mergeAndSaveMappings(project, baseTinyMappings, tinyMappings);
		} else {
			// These are merged v1 mappings
			Files.deleteIfExists(tinyMappings);
			project.getLogger().lifecycle(":populating field names");
			suggestFieldNames(minecraftProvider, baseTinyMappings, tinyMappings);
		}
	}

	private static boolean areMappingsV2(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			TinyV2Factory.readMetadata(reader);
			return true;
		} catch (IllegalArgumentException e) {
			// TODO: just check the mappings version when Parser supports V1 in readMetadata()
			return false;
		}
	}

	private static boolean doesJarContainV2Mappings(Path path) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(path, (ClassLoader) null)) {
			try (BufferedReader reader = Files.newBufferedReader(fs.getPath("mappings", "mappings.tiny"))) {
				TinyV2Factory.readMetadata(reader);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
	}

	private static void extractMappings(Path jar, Path extractTo) throws IOException {
		try (FileSystem unmergedIntermediaryFs = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
			extractMappings(unmergedIntermediaryFs, extractTo);
		}
	}

	public static void extractMappings(FileSystem jar, Path extractTo) throws IOException {
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}

	private void extractUnpickDefinitions(FileSystem jar, Path extractTo) throws IOException {
		Path unpickPath = jar.getPath("extras/definitions.unpick");
		Path unpickMetadataPath = jar.getPath("extras/unpick.json");

		if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
			return;
		}

		Files.copy(unpickPath, extractTo, StandardCopyOption.REPLACE_EXISTING);

		unpickMetadata = parseUnpickMetadata(unpickMetadataPath);
		hasUnpickDefinitions = true;
	}

	private UnpickMetadata parseUnpickMetadata(Path input) throws IOException {
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(input), JsonObject.class);

		if (!jsonObject.has("version") || jsonObject.get("version").getAsInt() != 1) {
			throw new UnsupportedOperationException("Unsupported unpick version");
		}

		return new UnpickMetadata(
				jsonObject.get("unpickGroup").getAsString(),
				jsonObject.get("unpickVersion").getAsString()
		);
	}

	private void populateUnpickClasspath() {
		String unpickCliName = "unpick-cli";
		getProject().getDependencies().add(Constants.Configurations.UNPICK_CLASSPATH,
				String.format("%s:%s:%s", unpickMetadata.unpickGroup, unpickCliName, unpickMetadata.unpickVersion)
		);
	}

	private void mergeAndSaveMappings(Project project, Path from, Path out) throws IOException {
		Stopwatch stopwatch = Stopwatch.createStarted();
		project.getLogger().info(":merging mappings");

		MemoryMappingTree tree = new MemoryMappingTree();
		MappingSourceNsSwitch sourceNsSwitch = new MappingSourceNsSwitch(tree, MappingNamespace.OFFICIAL.stringValue());
		readIntermediaryTree().accept(sourceNsSwitch);

		try (BufferedReader reader = Files.newBufferedReader(from, StandardCharsets.UTF_8)) {
			Tiny2Reader.read(reader, tree);
		}

		try (Tiny2Writer writer = new Tiny2Writer(Files.newBufferedWriter(out, StandardCharsets.UTF_8), false)) {
			tree.accept(writer);
		}

		project.getLogger().info(":merged mappings in " + stopwatch.stop());
	}

	private MemoryMappingTree readIntermediaryTree() throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingNsCompleter nsCompleter = new MappingNsCompleter(tree, Collections.singletonMap(MappingNamespace.NAMED.stringValue(), MappingNamespace.INTERMEDIARY.stringValue()), true);

		try (BufferedReader reader = Files.newBufferedReader(getIntermediaryTiny(), StandardCharsets.UTF_8)) {
			Tiny2Reader.read(reader, nsCompleter);
		}

		return tree;
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

	private void suggestFieldNames(MinecraftProviderImpl minecraftProvider, Path oldMappings, Path newMappings) {
		Command command = new CommandProposeFieldNames();
		runCommand(command, minecraftProvider.getMergedJar().getAbsolutePath(),
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

	private void initFiles() {
		mappingsWorkingDir = getMinecraftProvider().dir(mappingsIdentifier).toPath();
		baseTinyMappings = mappingsWorkingDir.resolve("mappings-base.tiny");
		tinyMappings = mappingsWorkingDir.resolve("mappings.tiny");
		tinyMappingsJar = mappingsWorkingDir.resolve("mappings.jar");
		unpickDefinitions = mappingsWorkingDir.resolve("mappings.unpick");

		if (isRefreshDeps()) {
			cleanFiles();
		}
	}

	public void cleanFiles() {
		try {
			if (Files.exists(mappingsWorkingDir)) {
				Files.walkFileTree(mappingsWorkingDir, new DeletingFileVisitor());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MAPPINGS;
	}

	public Path getIntermediaryTiny() throws IOException {
		if (intermediaryTiny == null) {
			intermediaryTiny = getMinecraftProvider().file("intermediary-v2.tiny").toPath();

			if (!Files.exists(intermediaryTiny) || (isRefreshDeps() && !hasRefreshed)) {
				hasRefreshed = true;

				// Download and extract intermediary
				String encodedMinecraftVersion = UrlEscapers.urlFragmentEscaper().escape(getMinecraftProvider().minecraftVersion());
				String intermediaryArtifactUrl = getExtension().getIntermediaryUrl(encodedMinecraftVersion);
				File intermediaryJar = getMinecraftProvider().file("intermediary-v2.jar");
				DownloadUtil.downloadIfChanged(new URL(intermediaryArtifactUrl), intermediaryJar, getProject().getLogger());
				extractMappings(intermediaryJar.toPath(), intermediaryTiny);
			}
		}

		return intermediaryTiny;
	}

	@Override
	public Path mappingsWorkingDir() {
		return mappingsWorkingDir;
	}

	private String createMappingsIdentifier(String mappingsName, String version, String classifier) {
		//          mappingsName      . mcVersion . version        classifier
		// Example: net.fabricmc.yarn . 1_16_5    . 1.16.5+build.5 -v2
		return mappingsName + "." + getMinecraftProvider().minecraftVersion().replace(' ', '_').replace('.', '_').replace('-', '_') + "." + version + classifier;
	}

	public String mappingsIdentifier() {
		return mappingsIdentifier;
	}

	public File getUnpickDefinitionsFile() {
		return unpickDefinitions.toFile();
	}

	public boolean hasUnpickDefinitions() {
		return hasUnpickDefinitions;
	}

	@Override
	public File intermediaryTinyFile() {
		try {
			return getIntermediaryTiny().toFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to get intermediary", e);
		}
	}

	public record UnpickMetadata(String unpickGroup, String unpickVersion) {
	}
}
