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
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.io.Files;
import com.google.gson.Gson;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.lorenztiny.TinyMappingFormat;

public class MojmapCreator {
	private static final Gson GSON = new Gson();
	private static final ClassLoader CLASS_LOADER = null;
	private static final Map<String, Object> CREATE = new HashMap<>();

	static {
		CREATE.put("create", "true");
	}

	public static Dependency mojmap(Project project, LoomGradleExtension extension, String minecraftVersionRaw) throws IOException {
		File userCache = extension.getUserCache();

		File mappingsDir = new File(userCache, "mappings");
		File steps = new File(mappingsDir, "steps");

		File manifests = new File(userCache, "version_manifest.json");
		DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), manifests, project.getLogger());
		String versionManifest = Files.asCharSource(manifests, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = GSON.fromJson(versionManifest, ManifestVersion.class);

		ManifestVersion.Versions version = mcManifest.versions.stream().filter(v -> v.id.equals(minecraftVersionRaw)).findAny().get();
		String id = version.id;

		File mappingsJar = new File(mappingsDir, "mojmap-" + id + ".jar");

		if (!mappingsJar.exists()) {
			File minecraftJson = new File(userCache, "minecraft-" + id + "-info.json");
			DownloadUtil.downloadIfChanged(new URL(version.url), minecraftJson, project.getLogger());

			MinecraftVersionInfo info = GSON.fromJson(Files.asCharSource(minecraftJson, StandardCharsets.UTF_8).read(), MinecraftVersionInfo.class);

			File clientMappings = new File(steps, "mojmap-" + id + "-client.txt");
			File serverMappings = new File(steps, "mojmap-" + id + "-server.txt");
			String clientMappingsUrl = info.downloads.get("client_mappings").url;
			String serverMappingsUrl = info.downloads.get("server_mappings").url;

			DownloadUtil.downloadIfChanged(new URL(clientMappingsUrl), clientMappings, project.getLogger());
			DownloadUtil.downloadIfChanged(new URL(serverMappingsUrl), serverMappings, project.getLogger());

			MappingSet mappings = MappingSet.create();

			try (
					ProGuardReader clientReader = new ProGuardReader(new FileReader(clientMappings));
					ProGuardReader serverReader = new ProGuardReader(new FileReader(serverMappings))) {
				clientReader.read(mappings);
				serverReader.read(mappings);
			}

			MappingSet officialToNamed = mappings.reverse();
			MappingSet officialToIntermediary;

			File intermediaryJar = new File(steps, "v2-intermediary-" + id + ".jar");
			DownloadUtil.downloadIfChanged(new URL(extension.getIntermediaryUrl().apply(id)), intermediaryJar, project.getLogger());

			try (FileSystem fileSystem = FileSystems.newFileSystem(intermediaryJar.toPath(), CLASS_LOADER)) {
				officialToIntermediary = TinyMappingFormat.DETECT.createReader(fileSystem.getPath("mappings/mappings.tiny"), "official", "intermediary").read();
			}

			try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + mappingsJar.toURI().toURL()), CREATE)) {
				Path path = fileSystem.getPath("mappings", "mappings.tiny");
				java.nio.file.Files.createDirectories(path.getParent());

				try (PrintWriter writer = new PrintWriter(java.nio.file.Files.newBufferedWriter(path))) {
					writer.print("v1\tofficial\tintermediary\tnamed");

					iterateClasses(officialToIntermediary, intermediaryClass -> {
						String officialName = intermediaryClass.getFullObfuscatedName();
						ClassMapping<?, ?> mojmapClass = officialToNamed.getClassMapping(officialName).map(x -> (ClassMapping) x).orElse(intermediaryClass);
						writer.print("\nCLASS\t" + officialName + "\t" + intermediaryClass.getFullDeobfuscatedName() + "\t" + mojmapClass.getFullDeobfuscatedName());

						for (FieldMapping intermediaryField : intermediaryClass.getFieldMappings()) {
							FieldSignature signature = intermediaryField.getSignature();
							FieldMapping mojmapField = mojmapClass.getFieldMapping(signature.getName()).orElse(intermediaryField);

							writer.print("\nFIELD\t" + officialName + "\t" + signature.getType().get() + "\t" + intermediaryField.getObfuscatedName() + "\t" + intermediaryField.getDeobfuscatedName() + "\t" + mojmapField.getDeobfuscatedName());
						}

						for (MethodMapping intermediaryMethod : intermediaryClass.getMethodMappings()) {
							MethodSignature signature = intermediaryMethod.getSignature();
							MethodMapping mojmapMethod = mojmapClass.getMethodMapping(signature).orElse(intermediaryMethod);

							writer.print("\nMETHOD\t" + officialName + "\t" + signature.getDescriptor() + "\t" + intermediaryMethod.getObfuscatedName() + "\t" + intermediaryMethod.getDeobfuscatedName() + "\t" + mojmapMethod.getDeobfuscatedName());
						}
					});

					writer.println();
				}
			}
		}

		DefaultModuleComponentIdentifier gradleId = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId("loom-generated", "mojmap-" + id), id);

		return new DefaultSelfResolvingDependency(gradleId, (FileCollectionInternal) project.files(mappingsJar)) {
			@Override
			public String getGroup() {
				return gradleId.getGroup();
			}

			@Override
			public String getName() {
				return gradleId.getModule();
			}

			@Override
			public String getVersion() {
				return gradleId.getVersion();
			}
		};
	}

	private static void iterateClasses(MappingSet mappings, Consumer<ClassMapping<?, ?>> consumer) {
		for (TopLevelClassMapping classMapping : mappings.getTopLevelClassMappings()) {
			iterateClass(classMapping, consumer);
		}
	}

	private static void iterateClass(ClassMapping<?, ?> classMapping, Consumer<ClassMapping<?, ?>> consumer) {
		consumer.accept(classMapping);

		for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
			iterateClass(innerClassMapping, consumer);
		}
	}
}
