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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.common.io.MoreFiles;
import com.google.gson.Gson;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsWriter;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.MethodParameterMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;

public class MojmapCreator {
	private static final Gson GSON = new Gson();

	public static Dependency mojmap(Project project, LoomGradleExtension extension, String minecraftVersionRaw) throws IOException {
		File userCache = extension.getUserCache();
		File mappingsDir = new File(userCache, "mappings");
		File steps = new File(mappingsDir, "steps");

		ManifestVersion.Versions version = MinecraftProvider.findVersion(project, userCache, minecraftVersionRaw).get();
		String id = version.id;

		File mappingsJar = new File(mappingsDir, "mojmap-" + id + ".jar");

		if (!mappingsJar.exists() || project.getGradle().getStartParameter().isRefreshDependencies()) {
			MappingSet officialToNamed;
			MappingSet intermediaryToOfficial;

			// Download and parse Proguard logs
			{
				File minecraftJson = new File(userCache, "minecraft-" + id + "-info.json");
				DownloadUtil.downloadIfChanged(new URL(version.url), minecraftJson, project.getLogger());

				MinecraftVersionInfo info = GSON.fromJson(MoreFiles.asCharSource(minecraftJson.toPath(), StandardCharsets.UTF_8).read(), MinecraftVersionInfo.class);

				File clientMappings = new File(steps, "mojmap-" + id + "-client.txt");
				File serverMappings = new File(steps, "mojmap-" + id + "-server.txt");
				String clientMappingsUrl = info.downloads.get("client_mappings").url;
				String serverMappingsUrl = info.downloads.get("server_mappings").url;

				DownloadUtil.downloadIfChanged(new URL(clientMappingsUrl), clientMappings, project.getLogger());
				DownloadUtil.downloadIfChanged(new URL(serverMappingsUrl), serverMappings, project.getLogger());

				MappingSet mappings = MappingSet.create();

				// Print license headers
				String clientMappingsString = MoreFiles.asCharSource(clientMappings.toPath(), StandardCharsets.UTF_8).read();
				String serverMappingsString = MoreFiles.asCharSource(serverMappings.toPath(), StandardCharsets.UTF_8).read();

				{
					project.getLogger().warn("USING OFFICIAL MAPPINGS. YOU ARE USING THESE MAPPINGS AT YOUR OWN RISK UNDER THE FOLLOWING LICENSE:");
					project.getLogger().warn("");

					for (String line : clientMappingsString.split("\\v+")) {
						if (line.startsWith("#")) {
							project.getLogger().warn(line);
						}
					}

					project.getLogger().warn("");
				}

				try (
						ProGuardReader clientReader = new ProGuardReader(new StringReader(clientMappingsString));
						ProGuardReader serverReader = new ProGuardReader(new StringReader(serverMappingsString))) {
					clientReader.read(mappings);
					serverReader.read(mappings);
				}

				officialToNamed = mappings.reverse();
			}

			// Download and parse intermediaries
			{
				File intermediaryJar = new File(steps, "v2-intermediary-" + id + ".jar");
				DownloadUtil.downloadIfChanged(new URL(extension.getIntermediaryUrl().apply(id)), intermediaryJar, project.getLogger());

				byte[] bytes = ZipUtil.unpackEntry(intermediaryJar, "mappings/mappings.tiny");

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
					intermediaryToOfficial = new TinyMappingsReader(TinyMappingFactory.loadWithDetection(reader), "intermediary", "official").read();
				}
			}

			MappingSet intermediaryToNamed = MappingSet.create();

			// Merging. Don't use MappingSet#merge
			iterateClasses(intermediaryToOfficial, i2oClassMapping -> {
				officialToNamed.getClassMapping(i2oClassMapping.getFullDeobfuscatedName()).ifPresent(o2nClassMapping -> {
					ClassMapping<?, ?> c = intermediaryToNamed.getOrCreateClassMapping(i2oClassMapping.getFullObfuscatedName())
							.setDeobfuscatedName(o2nClassMapping.getFullDeobfuscatedName());

					for (FieldMapping i2oFieldMapping : i2oClassMapping.getFieldMappings()) {
						o2nClassMapping.getFieldMapping(i2oFieldMapping.getDeobfuscatedName()).ifPresent(o2nFieldMapping -> {
							c.getOrCreateFieldMapping(i2oFieldMapping.getSignature())
									.setDeobfuscatedName(o2nFieldMapping.getDeobfuscatedName());
						});
					}

					for (MethodMapping i2oMethodMapping : i2oClassMapping.getMethodMappings()) {
						o2nClassMapping.getMethodMapping(i2oMethodMapping.getDeobfuscatedSignature()).ifPresent(o2nMethodMapping -> {
							c.getOrCreateMethodMapping(i2oMethodMapping.getSignature())
									.setDeobfuscatedName(o2nMethodMapping.getDeobfuscatedName());
						});
					}
				});
			});

			// Write
			{
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
					new TinyWriter(writer, "intermediary", "named").write(intermediaryToNamed);
				}

				ZipUtil.pack(new ZipEntrySource[] {new ByteSource("mappings/mappings.tiny", outputStream.toByteArray())}, mappingsJar);
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

	private static class TinyWriter extends TextMappingsWriter {
		private final String namespaceFrom;
		private final String namespaceTo;

		protected TinyWriter(Writer writer, String namespaceFrom, String namespaceTo) {
			super(writer);
			this.namespaceFrom = namespaceFrom;
			this.namespaceTo = namespaceTo;
		}

		@Override
		public void write(MappingSet mappings) {
			writer.println("tiny\t2\t0\t" + namespaceFrom + "\t" + namespaceTo);

			iterateClasses(mappings, classMapping -> {
				writer.println("c\t" + classMapping.getFullObfuscatedName() + "\t" + classMapping.getFullDeobfuscatedName());

				for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
					fieldMapping.getType().ifPresent(fieldType -> {
						writer.println("\tf\t" + fieldType + "\t" + fieldMapping.getObfuscatedName() + "\t" + fieldMapping.getDeobfuscatedName());
					});
				}

				for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
					writer.println("\tm\t" + methodMapping.getSignature().getDescriptor() + "\t" + methodMapping.getObfuscatedName() + "\t" + methodMapping.getDeobfuscatedName());

					for (MethodParameterMapping parameterMapping : methodMapping.getParameterMappings()) {
						// Lorenz doesn't support parameters having an obfuscated name
						writer.println("\t\tp\t" + parameterMapping.getIndex() + "\t\t\t" + parameterMapping.getDeobfuscatedName());
					}
				}
			});
		}
	}
}
