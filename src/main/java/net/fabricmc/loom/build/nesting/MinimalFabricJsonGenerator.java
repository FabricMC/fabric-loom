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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;

public abstract class MinimalFabricJsonGenerator extends DefaultTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(IncludedJarFactory.class);
	private static final String SEMVER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
	private static final Pattern SEMVER_PATTERN = Pattern.compile(SEMVER_REGEX);

	@Input
	protected abstract MapProperty<String, ComponentArtifactIdentifier> getJarIds();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	public void from(ArtifactCollection artifacts) {
		getJarIds().set(artifacts.getResolvedArtifacts().map(set -> {
			Map<String, ComponentArtifactIdentifier> map = new HashMap<>();
			set.forEach(artifact -> {
				map.put(artifact.getFile().getName(), artifact.getId());
			});
			return map;
		}));
	}

	@TaskAction
	void generateFabricJson() {
		try {
			File targetDir = getOutputDirectory().get().getAsFile();
			FileUtils.deleteDirectory(targetDir);
			targetDir.mkdirs();
		} catch (IOException e) {
			throw new org.gradle.api.UncheckedIOException(e);
		}
		getJarIds().get().forEach((fileName, id) -> {
			if (!(id instanceof ModuleComponentArtifactIdentifier moduleIdentifier)) {
				throw new RuntimeException("Attempted to nest artifact " + id + " which is not a module component.");
			}
			String group = moduleIdentifier.getComponentIdentifier().getGroup();
			String name = moduleIdentifier.getComponentIdentifier().getModule();
			String version = moduleIdentifier.getComponentIdentifier().getVersion();
			String classifier = null;
			if (moduleIdentifier.getFileName().startsWith(name + "-" + version + "-")) {
				String rest = moduleIdentifier.getFileName().substring(name.length() + version.length() + 2);
				int dotIndex = rest.indexOf('.');
				if (dotIndex != -1) {
					classifier = rest.substring(0, dotIndex);
				}
			}
			Metadata metadata = new Metadata(group, name, version, classifier);
			File targetFile = getOutputDirectory().file(fileName + ".json").get().getAsFile();
			try (OutputStream os = new FileOutputStream(targetFile)) {
				os.write(generateModForDependency(metadata).getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
                    throw new UncheckedIOException(e);
            }
		});
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
