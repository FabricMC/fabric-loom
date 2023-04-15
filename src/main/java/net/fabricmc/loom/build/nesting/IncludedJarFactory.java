/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.ZipUtils;

public final class IncludedJarFactory {
	private final Project project;

	public IncludedJarFactory(Project project) {
		this.project = project;
	}

	public Provider<ConfigurableFileCollection> getNestedJars(final Configuration configuration) {
		return project.provider(() -> {
			final ConfigurableFileCollection files = project.files();
			files.from(getFileDeps(configuration));
			files.builtBy(configuration.getBuildDependencies());
			return files;
		});
	}

	private ConfigurableFileCollection getFileDeps(Configuration configuration) {
		final ConfigurableFileCollection files = project.files();

		for (ResolvedArtifact resolvedArtifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			ModuleVersionIdentifier id = resolvedArtifact.getModuleVersion().getId();
			Metadata metadata = new Metadata(
					id.getGroup(),
					id.getName(),
					id.getVersion(),
					resolvedArtifact.getClassifier()
			);
			files.from(project.provider(() -> getNestableJar(resolvedArtifact.getFile(), metadata)));
		}
		return files;
	}

	private File getNestableJar(final File input, final Metadata metadata) {
		if (ModUtils.isMod(input)) {
			// Input is a mod, nothing needs to be done.
			return input;
		}

		LoomGradleExtension extension = LoomGradleExtension.get(project);
		File tempDir = new File(extension.getFiles().getUserCache(), "temp/modprocessing");

		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}

		File tempFile = new File(tempDir, input.getName());

		if (tempFile.exists()) {
			tempFile.delete();
		}

		try {
			FileUtils.copyFile(input, tempFile);
			ZipUtils.add(tempFile.toPath(), "fabric.mod.json", generateModForDependency(metadata).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to add dummy mod while including %s".formatted(input), e);
		}

		return tempFile;
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
		jsonObject.addProperty("version", metadata.version());
		jsonObject.addProperty("name", metadata.name());

		JsonObject custom = new JsonObject();
		custom.addProperty("fabric-loom:generated", true);
		jsonObject.add("custom", custom);

		return LoomGradlePlugin.GSON.toJson(jsonObject);
	}

	private record Metadata(String group, String name, String version, @Nullable String classifier) {
		@Override
		public String classifier() {
			if (classifier == null) {
				return "";
			} else {
				return "_" + classifier;
			}
		}
	}
}
