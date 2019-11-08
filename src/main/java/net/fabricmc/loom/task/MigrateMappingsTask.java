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

package net.fabricmc.loom.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.google.common.net.UrlEscapers;
import org.apache.commons.io.FileUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.Descriptored;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MigrateMappingsTask extends AbstractLoomTask {
	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Map<String, ?> properties = project.getProperties();

		project.getLogger().lifecycle(":loading mappings");

		String inputDir = (String) properties.get("inputDir");
		if (inputDir == null) inputDir = "src/main/java";
		String outputDir = (String) properties.get("outputDir");
		if (outputDir == null) outputDir = "remappedSrc";
		String officialMappingsVersion = (String) properties.get("targetMappings");
		String localMappingsPath = (String) properties.get("targetLocalMappings");

		if (officialMappingsVersion != null && localMappingsPath != null) {
			throw new IllegalArgumentException("targetMappings and targetLocalMappings are mutually exclusive;"
							+ " you either specify an official yarn mappings version with targetMappings, "
							+ "or a local one with targetLocalMappings.");
		}

		if (officialMappingsVersion == null && localMappingsPath == null) {
			throw new IllegalArgumentException("You must specify a new mappings version with -PtargetMappings (or local mappings with -PtargetLocalMappings).");
		}

		boolean useLocalMappings = localMappingsPath != null;

		if (useLocalMappings && !Files.exists(Paths.get(localMappingsPath))) {
			throw new IllegalArgumentException("Can't find local mappings in specified location: " + localMappingsPath);
		}

		String targetMappingsName = useLocalMappings ? localMappingsPath : officialMappingsVersion;

		Path inputDirPath = Paths.get(System.getProperty("user.dir"), inputDir);
		Path outputDirPath = Paths.get(System.getProperty("user.dir"), outputDir);

		if (!Files.exists(inputDirPath) || !Files.isDirectory(inputDirPath)) {
			throw new IllegalArgumentException("Could not find input directory: " + inputDirPath.toAbsolutePath());
		}

		Files.createDirectories(outputDirPath);

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		try {
			TinyTree currentMappings = mappingsProvider.getMappings();
			TinyTree targetMappings = getMappings(project, targetMappingsName, useLocalMappings);
			migrateMappings(project, extension.getMinecraftMappedProvider(), inputDirPath, outputDirPath, currentMappings, targetMappings, extension);
			project.getLogger().lifecycle(":remapped project written to " + outputDirPath.toAbsolutePath());
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not find mappings for version " + officialMappingsVersion, e);
		}
	}

	private TinyTree getMappings(Project project, String mappingsVersionOrPath, boolean useLocalMappings) throws IOException {
		Path migrateMappingsDir = Files.createTempDirectory("migrate");
		Path localMappingsOfVersion = migrateMappingsDir.resolve(mappingsVersionOrPath + ".tiny");

		if (!Files.exists(localMappingsOfVersion)) {
			if (useLocalMappings) {
				Files.copy(Paths.get(mappingsVersionOrPath), localMappingsOfVersion);
			} else {
				String versionRelativePath = UrlEscapers.urlFragmentEscaper().escape(mappingsVersionOrPath);
				String artifactUrl = "https://maven.fabricmc.net/net/fabricmc/yarn/" + versionRelativePath + "/yarn-" + versionRelativePath + ".jar";
				File mappingsJar = File.createTempFile("migrateMappingsJar", ".jar");
				project.getLogger().lifecycle(":downloading new mappings from " + artifactUrl);
				FileUtils.copyURLToFile(new URL(artifactUrl), mappingsJar);

				try (FileSystem jar = FileSystems.newFileSystem(mappingsJar.toPath(), null)) {
					if (!Files.exists(migrateMappingsDir)) Files.createDirectory(migrateMappingsDir);
					MappingsProvider.extractMappings(jar, localMappingsOfVersion);
				}
			}
		}

		try (BufferedReader reader = Files.newBufferedReader(localMappingsOfVersion)) {
			return TinyMappingFactory.loadWithDetection(reader);
		}
	}

	private static void migrateMappings(Project project, MinecraftMappedProvider minecraftMappedProvider,
										Path inputDir, Path outputDir, TinyTree currentMappings, TinyTree targetMappings, LoomGradleExtension extension
	) throws IOException {
		project.getLogger().lifecycle(":joining mappings");
		MappingSet mappingSet = new MappingsJoiner(currentMappings, targetMappings,
						"intermediary", "named").read();

		project.getLogger().lifecycle(":remapping");
		Mercury mercury = SourceRemapper.createMercuryWithClassPath(project, false);

		mercury.getClassPath().add(minecraftMappedProvider.MINECRAFT_MAPPED_JAR.toPath());
		mercury.getClassPath().add(minecraftMappedProvider.MINECRAFT_INTERMEDIARY_JAR.toPath());

		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		try {
			mercury.rewrite(inputDir, outputDir);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap fully!", e);
		}

		project.getLogger().lifecycle(":cleaning file descriptors");
		System.gc();
	}

	public static class MappingsJoiner extends MappingsReader {
		private final TinyTree sourceMappings, targetMappings;
		private final String fromNamespace, toNamespace;

		/**
		 * Say A is the source mappings and B is the target mappings.
		 * It does not map from intermediary to named but rather maps from named-A to named-B, by matching intermediary names.
		 * It goes through all of the intermediary names of A, and for every such intermediary name, call it I,
		 * matches the named mapping of I in A, with the named mapping of I in B.
		 * As you might imagine, this requires intermediary mappings to be stable across all versions.
		 * Since we only use intermediary names (and not descriptors) to match, and intermediary names are unique,
		 * this will migrate methods that have had their signature changed too.
		 */
		public MappingsJoiner(TinyTree sourceMappings, TinyTree targetMappings, String fromNamespace, String toNamespace) {
			this.sourceMappings = sourceMappings;
			this.targetMappings = targetMappings;
			this.fromNamespace = fromNamespace;
			this.toNamespace = toNamespace;
		}

		@Override
		public MappingSet read(MappingSet mappings) {
			Map<String, ClassDef> targetClasses = new HashMap<>();
			Map<String, FieldDef> targetFields = new HashMap<>();
			Map<String, MethodDef> targetMethods = new HashMap<>();

			for (ClassDef newClass : targetMappings.getClasses()) {
				targetClasses.put(newClass.getName(fromNamespace), newClass);

				for (FieldDef field : newClass.getFields()) {
					targetFields.put(field.getName(fromNamespace), field);
				}

				for (MethodDef method : newClass.getMethods()) {
					targetMethods.put(method.getName(fromNamespace), method);
				}
			}

			for (ClassDef oldClass : sourceMappings.getClasses()) {
				String namedMappingOfSourceMapping = oldClass.getName(toNamespace);
				String namedMappingOfTargetMapping = targetClasses.getOrDefault(oldClass.getName(fromNamespace), oldClass).getName(toNamespace);

				ClassMapping classMapping = mappings.getOrCreateClassMapping(namedMappingOfSourceMapping).setDeobfuscatedName(namedMappingOfTargetMapping);

				mapMembers(oldClass.getFields(), targetFields, classMapping::getOrCreateFieldMapping);
				mapMembers(oldClass.getMethods(), targetMethods, classMapping::getOrCreateMethodMapping);
			}

			return mappings;
		}

		private <T extends Descriptored> void mapMembers(Collection<T> oldMembers, Map<String, T> newMembers,
														BiFunction<String, String, Mapping> mapper) {
			for (T oldMember : oldMembers) {
				String oldName = oldMember.getName(toNamespace);
				String oldDescriptor = oldMember.getDescriptor(toNamespace);
				// We only use the intermediary name (and not the descriptor) because every method has a unique intermediary name
				String newName = newMembers.getOrDefault(oldMember.getName(fromNamespace), oldMember).getName(toNamespace);

				mapper.apply(oldName, oldDescriptor).setDeobfuscatedName(newName);
			}
		}

		@Override
		public void close() {
		}
	}
}

