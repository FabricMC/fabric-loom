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
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.google.common.base.Stopwatch;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.Tiny2Reader;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;

public class MappingsProviderImpl implements MappingsProvider {
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
	private MemoryMappingTree mappingTree;
	private Map<String, String> signatureFixes;

	private final Project project;
	private final MinecraftProvider minecraftProvider;
	private final LoomGradleExtension extension;
	public MappingsProviderImpl(Project project, MinecraftProvider minecraftProvider) {
		this.project = project;
		this.minecraftProvider = minecraftProvider;
		this.extension = LoomGradleExtension.get(project);
	}

	public MemoryMappingTree getMappings() throws IOException {
		return Objects.requireNonNull(mappingTree, "Cannot get mappings before they have been read");
	}

	public void provide() throws Exception {
		final DependencyInfo dependency = DependencyInfo.create(project, Constants.Configurations.MAPPINGS);

		project.getLogger().info(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		File mappingsJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find yarn mappings: " + dependency));

		String mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");
		boolean isV2 = isV2(dependency, mappingsJar);
		this.mappingsIdentifier = createMappingsIdentifier(mappingsName, version, getMappingsClassifier(dependency, isV2));

		initFiles();

		if (Files.notExists(tinyMappings) || isRefreshDeps()) {
			storeMappings(project, minecraftProvider, mappingsJar.toPath());
		} else {
			try (FileSystem fileSystem = FileSystems.newFileSystem(mappingsJar.toPath(), (ClassLoader) null)) {
				extractExtras(fileSystem);
			}
		}

		mappingTree = readMappings();

		if (Files.notExists(tinyMappingsJar) || isRefreshDeps()) {
			Files.deleteIfExists(tinyMappingsJar);
			ZipUtils.add(tinyMappingsJar, "mappings/mappings.tiny", Files.readAllBytes(tinyMappings));
		}

		if (hasUnpickDefinitions()) {
			String notation = String.format("%s:%s:%s:constants",
					dependency.getDependency().getGroup(),
					dependency.getDependency().getName(),
					dependency.getDependency().getVersion()
			);

			project.getDependencies().add(Constants.Configurations.MAPPING_CONSTANTS, notation);
			populateUnpickClasspath();
		}

		project.getDependencies().add(Constants.Configurations.MAPPINGS_FINAL, project.files(tinyMappingsJar.toFile()));
	}

	private String getMappingsClassifier(DependencyInfo dependency, boolean isV2) {
		String[] depStringSplit = dependency.getDepString().split(":");

		if (depStringSplit.length >= 4) {
			return "-" + depStringSplit[3] + (isV2 ? "-v2" : "");
		}

		return isV2 ? "-v2" : "";
	}

	private boolean isV2(DependencyInfo dependency, File mappingsJar) throws IOException {
		String minecraftVersion = minecraftProvider.minecraftVersion();

		// Only do this for official yarn, there isn't really a way we can get the mc version for all mappings
		if (dependency.getDependency().getGroup() != null && dependency.getDependency().getGroup().equals("net.fabricmc") && dependency.getDependency().getName().equals("yarn") && dependency.getDependency().getVersion() != null) {
			String yarnVersion = dependency.getDependency().getVersion();
			char separator = yarnVersion.contains("+build.") ? '+' : yarnVersion.contains("-") ? '-' : '.';
			String yarnMinecraftVersion = yarnVersion.substring(0, yarnVersion.lastIndexOf(separator));

			if (!yarnMinecraftVersion.equalsIgnoreCase(minecraftVersion)) {
				project.getLogger().warn("Minecraft Version ({}) does not match yarn's minecraft version ({})", minecraftVersion, yarnMinecraftVersion);
			}

			// We can save reading the zip file + header by checking the file name
			return mappingsJar.getName().endsWith("-v2.jar");
		} else {
			return doesJarContainV2Mappings(mappingsJar.toPath());
		}
	}

	private void storeMappings(Project project, MinecraftProvider minecraftProvider, Path yarnJar) throws IOException {
		project.getLogger().info(":extracting " + yarnJar.getFileName());

		try (FileSystem fileSystem = FileSystems.newFileSystem(yarnJar, (ClassLoader) null)) {
			extractMappings(fileSystem, baseTinyMappings);
			extractExtras(fileSystem);
		}

		if (areMappingsV2(baseTinyMappings)) {
			// These are unmerged v2 mappings
			mergeAndSaveMappings(project, baseTinyMappings, tinyMappings);
		} else {
			if (minecraftProvider instanceof MergedMinecraftProvider mergedMinecraftProvider) {
				// These are merged v1 mappings
				Files.deleteIfExists(tinyMappings);
				project.getLogger().lifecycle(":populating field names");
				suggestFieldNames(mergedMinecraftProvider, baseTinyMappings, tinyMappings);
			} else {
				throw new UnsupportedOperationException("V1 mappings only support merged minecraft");
			}
		}
	}

	private MemoryMappingTree readMappings() throws IOException {
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		MappingReader.read(tinyMappings, mappingTree);
		return mappingTree;
	}

	private static boolean areMappingsV2(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			return MappingReader.detectFormat(reader) == MappingFormat.TINY_2;
		}
	}

	private static boolean doesJarContainV2Mappings(Path path) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(path, (ClassLoader) null)) {
			try (BufferedReader reader = Files.newBufferedReader(fs.getPath("mappings", "mappings.tiny"))) {
				return MappingReader.detectFormat(reader) == MappingFormat.TINY_2;
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

	private void extractExtras(FileSystem jar) throws IOException {
		extractUnpickDefinitions(jar);
		extractSignatureFixes(jar);
	}

	private void extractUnpickDefinitions(FileSystem jar) throws IOException {
		Path unpickPath = jar.getPath("extras/definitions.unpick");
		Path unpickMetadataPath = jar.getPath("extras/unpick.json");

		if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
			return;
		}

		Files.copy(unpickPath, unpickDefinitions, StandardCopyOption.REPLACE_EXISTING);

		unpickMetadata = parseUnpickMetadata(unpickMetadataPath);
		hasUnpickDefinitions = true;
	}

	private void extractSignatureFixes(FileSystem jar) throws IOException {
		Path recordSignaturesJsonPath = jar.getPath("extras/record_signatures.json");

		if (!Files.exists(recordSignaturesJsonPath)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(recordSignaturesJsonPath, StandardCharsets.UTF_8)) {
			//noinspection unchecked
			signatureFixes = LoomGradlePlugin.OBJECT_MAPPER.readValue(reader, Map.class);
		}
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
		project.getDependencies().add(Constants.Configurations.UNPICK_CLASSPATH,
				String.format("%s:%s:%s", unpickMetadata.unpickGroup, unpickCliName, unpickMetadata.unpickVersion)
		);

		// Unpick ships with a slightly older version of asm, ensure it runs with at least the same version as loom.
		String[] asmDeps = new String[] {
				"org.ow2.asm:asm:%s",
				"org.ow2.asm:asm-tree:%s",
				"org.ow2.asm:asm-commons:%s",
				"org.ow2.asm:asm-util:%s"
		};

		for (String asm : asmDeps) {
			project.getDependencies().add(Constants.Configurations.UNPICK_CLASSPATH,
					asm.formatted(Opcodes.class.getPackage().getImplementationVersion())
			);
		}
	}

	private void mergeAndSaveMappings(Project project, Path from, Path out) throws IOException {
		Stopwatch stopwatch = Stopwatch.createStarted();
		project.getLogger().info(":merging mappings");

		MemoryMappingTree intermediaryTree = new MemoryMappingTree();
		readIntermediaryTree().accept(new MappingSourceNsSwitch(intermediaryTree, MappingsNamespace.INTERMEDIARY.toString()));

		try (BufferedReader reader = Files.newBufferedReader(from, StandardCharsets.UTF_8)) {
			Tiny2Reader.read(reader, intermediaryTree);
		}

		MemoryMappingTree officialTree = new MemoryMappingTree();
		MappingNsCompleter nsCompleter = new MappingNsCompleter(officialTree, Map.of(MappingsNamespace.OFFICIAL.toString(), MappingsNamespace.INTERMEDIARY.toString()));
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(nsCompleter, MappingsNamespace.OFFICIAL.toString());
		intermediaryTree.accept(nsSwitch);

		inheritMappedNamesOfEnclosingClasses(officialTree);

		try (Tiny2Writer writer = new Tiny2Writer(Files.newBufferedWriter(out, StandardCharsets.UTF_8), false)) {
			officialTree.accept(writer);
		}

		project.getLogger().info(":merged mappings in " + stopwatch.stop());
	}

	/**
	 * Searches the mapping tree for inner classes with no mapped name, whose enclosing classes have mapped names.
	 * Currently, Yarn does not export mappings for these inner classes.
	 */
	private void inheritMappedNamesOfEnclosingClasses(MemoryMappingTree tree) {
		int intermediaryIdx = tree.getNamespaceId("intermediary");
		int namedIdx = tree.getNamespaceId("named");

		// The tree does not have an index by intermediary names by default
		tree.setIndexByDstNames(true);

		for (MappingTree.ClassMapping classEntry : tree.getClasses()) {
			String intermediaryName = classEntry.getDstName(intermediaryIdx);
			String namedName = classEntry.getDstName(namedIdx);

			if (intermediaryName.equals(namedName) && intermediaryName.contains("$")) {
				String[] path = intermediaryName.split(Pattern.quote("$"));
				int parts = path.length;

				for (int i = parts - 2; i >= 0; i--) {
					String currentPath = String.join("$", Arrays.copyOfRange(path, 0, i + 1));
					String namedParentClass = tree.mapClassName(currentPath, intermediaryIdx, namedIdx);

					if (!namedParentClass.equals(currentPath)) {
						classEntry.setDstName(namedParentClass
										+ "$" + String.join("$", Arrays.copyOfRange(path, i + 1, path.length)),
								namedIdx);
						break;
					}
				}
			}
		}
	}

	private MemoryMappingTree readIntermediaryTree() throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingNsCompleter nsCompleter = new MappingNsCompleter(tree, Collections.singletonMap(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);

		try (BufferedReader reader = Files.newBufferedReader(getIntermediaryTiny(), StandardCharsets.UTF_8)) {
			Tiny2Reader.read(reader, nsCompleter);
		}

		return tree;
	}

	private void suggestFieldNames(MergedMinecraftProvider minecraftProvider, Path oldMappings, Path newMappings) {
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
		mappingsWorkingDir = minecraftProvider.dir(mappingsIdentifier).toPath();
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

			Files.createDirectories(mappingsWorkingDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Path getIntermediaryTiny() throws IOException {
		if (intermediaryTiny == null) {
			intermediaryTiny = minecraftProvider.file("intermediary-v2.tiny").toPath();

			if (!Files.exists(intermediaryTiny) || (isRefreshDeps() && !hasRefreshed)) {
				hasRefreshed = true;

				// Download and extract intermediary
				String encodedMinecraftVersion = UrlEscapers.urlFragmentEscaper().escape(minecraftProvider.minecraftVersion());
				String intermediaryArtifactUrl = extension.getIntermediaryUrl(encodedMinecraftVersion);
				File intermediaryJar = minecraftProvider.file("intermediary-v2.jar");
				DownloadUtil.downloadIfChanged(new URL(intermediaryArtifactUrl), intermediaryJar, project.getLogger());
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
		return mappingsName + "." + minecraftProvider.minecraftVersion().replace(' ', '_').replace('.', '_').replace('-', '_') + "." + version + classifier;
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

	@Nullable
	public Map<String, String> getSignatureFixes() {
		return signatureFixes;
	}

	@Override
	public File intermediaryTinyFile() {
		try {
			return getIntermediaryTiny().toFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to get intermediary", e);
		}
	}

	public String getBuildServiceName(String name, String from, String to) {
		return "%s:%s:%s>%S".formatted(name, mappingsIdentifier(), from, to);
	}

	public record UnpickMetadata(String unpickGroup, String unpickVersion) {
	}

	protected boolean isRefreshDeps() {
		return LoomGradlePlugin.refreshDeps;
	}
}
