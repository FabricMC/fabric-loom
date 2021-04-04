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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsWriter;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.tasks.TaskDependency;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;

public class MojangMappingsDependency extends AbstractModuleDependency implements SelfResolvingDependency, ExternalModuleDependency {
	public static final String GROUP = "net.minecraft";
	public static final String MODULE = "mappings";
	// Keys in dependency manifest
	private static final String MANIFEST_CLIENT_MAPPINGS = "client_mappings";
	private static final String MANIFEST_SERVER_MAPPINGS = "server_mappings";

	private final Project project;
	private final LoomGradleExtension extension;

	private boolean changing;
	private boolean force;

	public MojangMappingsDependency(Project project, LoomGradleExtension extension) {
		super(null);
		this.project = project;
		this.extension = extension;
	}

	@Override
	public ExternalModuleDependency copy() {
		MojangMappingsDependency copiedProjectDependency = new MojangMappingsDependency(project, extension);
		this.copyTo(copiedProjectDependency);
		return copiedProjectDependency;
	}

	@Override
	public void version(Action<? super MutableVersionConstraint> action) {
	}

	@Override
	public boolean isForce() {
		return this.force;
	}

	@Override
	public ExternalModuleDependency setForce(boolean force) {
		this.validateMutation(this.force, force);
		this.force = force;
		return this;
	}

	@Override
	public boolean isChanging() {
		return this.changing;
	}

	@Override
	public ExternalModuleDependency setChanging(boolean changing) {
		this.validateMutation(this.changing, changing);
		this.changing = changing;
		return this;
	}

	@Override
	public Set<File> resolve() {
		Path mappingsDir = extension.getMappingsProvider().getMappingsDir();
		Path mappingsFile = mappingsDir.resolve(String.format("%s.%s-%s.tiny", GROUP, MODULE, getVersion()));
		Path clientMappings = mappingsDir.resolve(String.format("%s.%s-%s-client.map", GROUP, MODULE, getVersion()));
		Path serverMappings = mappingsDir.resolve(String.format("%s.%s-%s-server.map", GROUP, MODULE, getVersion()));

		if (!Files.exists(mappingsFile) || LoomGradlePlugin.refreshDeps) {
			MappingSet mappingSet;

			try {
				mappingSet = getMappingsSet(clientMappings, serverMappings);

				try (Writer writer = new StringWriter()) {
					new TinyWriter(writer, "intermediary", "named").write(mappingSet);
					Files.deleteIfExists(mappingsFile);

					ZipUtil.pack(new ZipEntrySource[] {
							new ByteSource("mappings/mappings.tiny", writer.toString().getBytes(StandardCharsets.UTF_8))
					}, mappingsFile.toFile());
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to resolve Mojang mappings", e);
			}
		}

		if (!extension.isSilentMojangMappingsLicenseEnabled()) {
			try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8)) {
				project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
				project.getLogger().warn("Using of the official minecraft mappings is at your own risk!");
				project.getLogger().warn("Please make sure to read and understand the following license:");
				project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
				String line;

				while ((line = clientBufferedReader.readLine()).startsWith("#")) {
					project.getLogger().warn(line);
				}

				project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			} catch (IOException e) {
				throw new RuntimeException("Failed to read client mappings", e);
			}
		}

		return Collections.singleton(mappingsFile.toFile());
	}

	private MappingSet getMappingsSet(Path clientMappings, Path serverMappings) throws IOException {
		MinecraftVersionMeta versionInfo = extension.getMinecraftProvider().getVersionInfo();

		if (versionInfo.getDownload(MANIFEST_CLIENT_MAPPINGS) == null) {
			throw new RuntimeException("Failed to find official mojang mappings for " + getVersion());
		}

		MinecraftVersionMeta.Download clientMappingsDownload = versionInfo.getDownload(MANIFEST_CLIENT_MAPPINGS);
		MinecraftVersionMeta.Download serverMappingsDownload = versionInfo.getDownload(MANIFEST_CLIENT_MAPPINGS);

		HashedDownloadUtil.downloadIfInvalid(new URL(clientMappingsDownload.getUrl()), clientMappings.toFile(), clientMappingsDownload.getSha1(), project.getLogger(), false);
		HashedDownloadUtil.downloadIfInvalid(new URL(serverMappingsDownload.getUrl()), serverMappings.toFile(), clientMappingsDownload.getSha1(), project.getLogger(), false);

		MappingSet mappings = MappingSet.create();

		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8);
				BufferedReader serverBufferedReader = Files.newBufferedReader(serverMappings, StandardCharsets.UTF_8)) {
			try (ProGuardReader proGuardReaderClient = new ProGuardReader(clientBufferedReader);
					ProGuardReader proGuardReaderServer = new ProGuardReader(serverBufferedReader)) {
				proGuardReaderClient.read(mappings);
				proGuardReaderServer.read(mappings);
			}
		}

		MappingSet officialToNamed = mappings.reverse();
		MappingSet intermediaryToOfficial;

		try (BufferedReader reader = Files.newBufferedReader(extension.getMappingsProvider().getIntermediaryTiny(), StandardCharsets.UTF_8)) {
			intermediaryToOfficial = new TinyMappingsReader(TinyMappingFactory.loadWithDetection(reader), "intermediary", "official").read();
		}

		MappingSet intermediaryToMojang = MappingSet.create();

		// Merging. Don't use MappingSet#merge
		iterateClasses(intermediaryToOfficial, inputMappings -> {
			officialToNamed.getClassMapping(inputMappings.getFullDeobfuscatedName())
					.ifPresent(namedClass -> {
						ClassMapping<?, ?> mojangClassMapping = intermediaryToMojang.getOrCreateClassMapping(inputMappings.getFullObfuscatedName())
								.setDeobfuscatedName(namedClass.getFullDeobfuscatedName());

						for (FieldMapping fieldMapping : inputMappings.getFieldMappings()) {
							namedClass.getFieldMapping(fieldMapping.getDeobfuscatedName())
									.ifPresent(namedField -> {
										mojangClassMapping.getOrCreateFieldMapping(fieldMapping.getSignature())
												.setDeobfuscatedName(namedField.getDeobfuscatedName());
									});
						}

						for (MethodMapping methodMapping : inputMappings.getMethodMappings()) {
							namedClass.getMethodMapping(methodMapping.getDeobfuscatedSignature())
									.ifPresent(namedMethod -> {
										mojangClassMapping.getOrCreateMethodMapping(methodMapping.getSignature())
												.setDeobfuscatedName(namedMethod.getDeobfuscatedName());
									});
						}
					});
		});

		return intermediaryToMojang;
	}

	@Override
	public Set<File> resolve(boolean transitive) {
		return resolve();
	}

	@Override
	public TaskDependency getBuildDependencies() {
		return task -> Collections.emptySet();
	}

	@Override
	public String getGroup() {
		return GROUP;
	}

	@Override
	public String getName() {
		return MODULE;
	}

	@Override
	public String getVersion() {
		if (extension.getDependencyManager() == null) return "1.0.0";
		return extension.getMinecraftProvider().getMinecraftVersion();
	}

	@Override
	public VersionConstraint getVersionConstraint() {
		return new DefaultMutableVersionConstraint(getVersion());
	}

	@Override
	public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
		return (new ModuleVersionSelectorStrictSpec(this)).isSatisfiedBy(identifier);
	}

	@Override
	public ModuleIdentifier getModule() {
		return DefaultModuleIdentifier.newId(GROUP, MODULE);
	}

	@Override
	public boolean contentEquals(Dependency dependency) {
		if (dependency instanceof MojangMappingsDependency) {
			return ((MojangMappingsDependency) dependency).extension.getMinecraftProvider().getMinecraftVersion().equals(getVersion());
		}

		return false;
	}

	@Override
	public String getReason() {
		return null;
	}

	@Override
	public void because(String s) {
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
				}
			});
		}
	}
}
