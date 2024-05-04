/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public abstract class NestableJarGenerationTask extends DefaultTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(NestableJarGenerationTask.class);
	private static final String SEMVER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
	private static final Pattern SEMVER_PATTERN = Pattern.compile(SEMVER_REGEX);

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	protected abstract ConfigurableFileCollection getJars();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	protected abstract MapProperty<String, Metadata> getJarIds();

	@TaskAction
	void makeNestableJars() {
		Map<String, String> fabricModJsons = new HashMap<>();
		getJarIds().get().forEach((fileName, metadata) -> {
			fabricModJsons.put(fileName, generateModForDependency(metadata));
		});

		try {
			File targetDir = getOutputDirectory().get().getAsFile();
			FileUtils.deleteDirectory(targetDir);
			targetDir.mkdirs();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		getJars().forEach(file -> {
			File targetFile = getOutputDirectory().file(file.getName()).get().getAsFile();
			targetFile.delete();
			String fabricModJson = Objects.requireNonNull(fabricModJsons.get(file.getName()), "Could not generate fabric.mod.json for included dependency "+file.getName());
			makeNestableJar(file, targetFile, fabricModJson);
		});
	}

	public void from(Configuration configuration) {
		ArtifactView artifacts = configuration.getIncoming().artifactView(config -> {
			config.attributes(
					attr -> attr.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
			);
		});
		getJars().from(artifacts.getFiles());
		dependsOn(configuration);
		getJarIds().set(artifacts.getArtifacts().getResolvedArtifacts().map(set -> {
			Map<String, Metadata> map = new HashMap<>();
			set.forEach(artifact -> {
				ResolvedVariantResult variant = artifact.getVariant();

				ComponentIdentifier id = variant.getOwner();
				Metadata moduleLocation = null;

				if (id instanceof ModuleComponentIdentifier moduleIdentifier) {
					moduleLocation = new Metadata(
							moduleIdentifier.getGroup(),
							moduleIdentifier.getModule(),
							moduleIdentifier.getVersion(),
							null
					);
				}

				List<Metadata> capabilityLocations = variant.getCapabilities().stream()
						.map(capability -> new Metadata(capability.getGroup(), capability.getName(), capability.getVersion(), null))
						.toList();

				if (!capabilityLocations.isEmpty() && (moduleLocation == null || !capabilityLocations.contains(moduleLocation))) {
					moduleLocation = capabilityLocations.get(0);
				}

				if (moduleLocation == null) {
					throw new RuntimeException("Attempted to nest artifact " + id + " which is not a module component and has no capabilities.");
				} else if (moduleLocation.version == null) {
					throw new RuntimeException("Attempted to nest artifact " + id + " which has no version");
				}

				String group = moduleLocation.group;
				String name = moduleLocation.name;
				String version = moduleLocation.version;
				String classifier = null;

				if (artifact.getFile().getName().startsWith(name + "-" + version + "-")) {
					String rest = artifact.getFile().getName().substring(name.length() + version.length() + 2);
					int dotIndex = rest.indexOf('.');

					if (dotIndex != -1) {
						classifier = rest.substring(0, dotIndex);
					}
				}

				Metadata metadata = new Metadata(group, name, version, classifier);
				map.put(artifact.getFile().getName(), metadata);
			});
			return map;
		}));
	}

	// Generates a barebones mod for a dependency
	private static String generateModForDependency(Metadata metadata) {
		String modId = (metadata.group() + "_" + metadata.name() + metadata.classifier())
				.replaceAll("\\.", "_")
				.toLowerCase(Locale.ENGLISH);

		// Fabric Loader can't handle modIds longer than 64 characters
		if (modId.length() > 64) {
			String hash = Hashing.sha256()
					.hashString(modId, StandardCharsets.UTF_8)
					.toString();
			modId = modId.substring(0, 50) + hash.substring(0, 14);
		}

		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("schemaVersion", 1);

		jsonObject.addProperty("id", modId);
		String version = getVersion(metadata);
		jsonObject.addProperty("version", version);
		jsonObject.addProperty("name", metadata.name());

		JsonObject custom = new JsonObject();
		custom.addProperty("fabric-loom:generated", true);
		jsonObject.add("custom", custom);

		return LoomGradlePlugin.GSON.toJson(jsonObject);
	}

	private static String getVersion(Metadata metadata) {
		String version = metadata.version();

		if (validSemVer(version)) {
			return version;
		}

		if (version.endsWith(".Final") || version.endsWith(".final")) {
			String trimmedVersion = version.substring(0, version.length() - 6);

			if (validSemVer(trimmedVersion)) {
				return trimmedVersion;
			}
		}

		LOGGER.warn("({}) is not valid semver for dependency {}", version, metadata);
		return version;
	}

	private static boolean validSemVer(String version) {
		Matcher matcher = SEMVER_PATTERN.matcher(version);
		return matcher.find();
	}

	private void makeNestableJar(final File input, final File output, final String modJsonFile) {
		try {
			FileUtils.copyFile(input, output);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to copy mod file %s".formatted(input), e);
		}

		if (FabricModJsonFactory.isModJar(input)) {
			// Input is a mod, nothing needs to be done.
			return;
		}

		try {
			ZipReprocessorUtil.appendZipEntry(output.toPath(), "fabric.mod.json", modJsonFile.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to add dummy mod while including %s".formatted(input), e);
		}
	}

	protected record Metadata(String group, String name, String version, @Nullable String classifier) implements Serializable {
		@Override
		public String classifier() {
			if (classifier == null) {
				return "";
			} else {
				return "_" + classifier;
			}
		}

		@Override
		public String toString() {
			return group + ":" + name + ":" + version + classifier();
		}
	}
}
