/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.processors.speccontext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.fmj.FabricModJsonSource;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public final class ModMetadata {
	private static final String CONFIGURATION = "loomSourceModMetadata";
	static final String USAGE = "fabric-loom-source-mod-metadata";

	private ModMetadata() {
	}

	public static void register(Project project) {
		project.getConfigurations().consumable(CONFIGURATION, configuration -> {
			configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, USAGE)));
			configuration.getOutgoing().getArtifacts().addAllLater(project.provider(() -> sourceArtifacts(project)));
		});
	}

	private static List<PublishArtifact> sourceArtifacts(Project project) {
		var source = findSource(project).orElse(null);

		if (source == null) {
			return List.of();
		}

		Path metadataFile = source.fabricModJsonPath();
		FabricModJson mod = readModJson(project, metadataFile, source);
		List<PublishArtifact> artifacts = new ArrayList<>();
		artifacts.add(new ModMetadataArtifact(metadataFile.toFile(), ModMetadataArtifactType.METADATA, "metadata"));

		for (String classTweaker : mod.getClassTweakers().keySet()) {
			artifacts.add(resourceArtifact(source, classTweaker, ModMetadataArtifactType.CLASS_TWEAKER));
		}

		JsonElement javadoc = mod.getCustom(Constants.CustomModJsonKeys.PROVIDED_JAVADOC);

		if (javadoc != null) {
			artifacts.add(resourceArtifact(source, javadoc.getAsString(), ModMetadataArtifactType.PROVIDED_JAVADOC));
		}

		return artifacts;
	}

	private static Optional<FabricModJsonSource.SourceSetSource> findSource(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getFabricModJsonPath().isPresent()) {
			Path metadataFile = extension.getFabricModJsonPath().get().getAsFile().toPath();
			return Optional.of(new FabricModJsonSource.SourceSetSource(metadataFile, List.of(metadataFile.getParent())));
		}

		List<SourceSet> sourceSets = new ArrayList<>();
		sourceSets.add(extension.getMinecraftSourceSet());

		if (extension.areEnvironmentSourceSetsSplit()) {
			sourceSets.add(extension.getMinecraftClientSourceSet());
		}

		SourceSet[] resourceSourceSets = sourceSets.toArray(SourceSet[]::new);
		File metadataFile = SourceSetHelper.findFirstFileInResource(FabricModJsonFactory.FABRIC_MOD_JSON, project, resourceSourceSets);

		if (metadataFile == null) {
			return Optional.empty();
		}

		List<Path> resourceRoots = SourceSetHelper.getResourceRoots(resourceSourceSets);
		return Optional.of(new FabricModJsonSource.SourceSetSource(metadataFile.toPath(), resourceRoots));
	}

	private static ModMetadataArtifact resourceArtifact(FabricModJsonSource.SourceSetSource source, String path, ModMetadataArtifactType type) {
		try {
			Path resource = source.resolve(path);
			return new ModMetadataArtifact(resource.toFile(), type, path);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to find source metadata resource " + path + " in " + source.fabricModJsonPath(), e);
		}
	}

	static FabricModJson readModJson(Project project, Path metadataFile, FabricModJsonSource source) {
		String json = new String(readConfigurationInput(project, metadataFile), StandardCharsets.UTF_8);
		JsonObject modJson = LoomGradlePlugin.GSON.fromJson(json, JsonObject.class);
		return FabricModJsonFactory.create(modJson, source);
	}

	/// Reading through Gradle tracks configuration-cache inputs
	static byte[] readConfigurationInput(Project project, Path path) {
		return project.getProviders().fileContents(project.getLayout().file(project.provider(path::toFile))).getAsBytes().get();
	}
}
