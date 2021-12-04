/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration.processors.dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.ZipUtils;

public class ModDependencyInfo {
	private final String group;
	public final String name;
	public final String version;
	@Nullable
	public final String classifier;
	public final File inputFile;
	public final Configuration targetConfig;
	public final RemapData remapData;

	@Nullable
	private final AccessWidenerData accessWidenerData;

	private boolean forceRemap = false;

	public ModDependencyInfo(String group, String name, String version, @Nullable String classifier, File inputFile, Configuration targetConfig, RemapData remapData) {
		this.group = group;
		this.name = name;
		this.version = version;
		this.classifier = classifier;
		this.inputFile = inputFile;
		this.targetConfig = targetConfig;
		this.remapData = remapData;

		try {
			this.accessWidenerData = tryReadAccessWidenerData(getInputFile().toPath());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read access widener data from" + inputFile, e);
		}
	}

	public String getRemappedNotation() {
		if (!hasClassifier()) {
			return String.format("%s:%s:%s", getGroup(), name, version);
		}

		return String.format("%s:%s:%s:%s", getGroup(), name, version, classifier);
	}

	public String getRemappedFilename(boolean withClassifier) {
		if (!hasClassifier() || !withClassifier) {
			return String.format("%s-%s", name, version);
		}

		return String.format("%s-%s-%s", name, version, classifier);
	}

	public File getRemappedDir() {
		return new File(remapData.modStore(), String.format("%s/%s/%s", getGroup().replace(".", "/"), name, version));
	}

	public File getRemappedOutput() {
		return new File(getRemappedDir(), getRemappedFilename(true) + ".jar");
	}

	public File getRemappedOutput(String classifier) {
		return new File(getRemappedDir(), getRemappedFilename(false) + "-" + classifier + ".jar");
	}

	private File getRemappedPom() {
		return new File(getRemappedDir(), String.format("%s-%s", name, version) + ".pom");
	}

	private String getGroup() {
		return getMappingsPrefix(remapData.mappingsSuffix()) + "." + group;
	}

	public static String getMappingsPrefix(String mappings) {
		return mappings.replace(".", "_").replace("-", "_").replace("+", "_");
	}

	public File getInputFile() {
		return inputFile;
	}

	private boolean outputHasInvalidAccessWidener() {
		if (accessWidenerData == null) {
			// This mod doesn't use an AW
			return false;
		}

		assert getRemappedOutput().exists();
		final AccessWidenerData outputAWData;

		try {
			outputAWData = tryReadAccessWidenerData(getRemappedOutput().toPath());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read output access widener data from " + getRemappedOutput(), e);
		}

		if (outputAWData == null) {
			// We know for sure the input has an AW, something is wrong if the output hasn't got one.
			return true;
		}

		// The output jar must have an AW in the "named" namespace.
		return !MappingsNamespace.NAMED.toString().equals(outputAWData.header().getNamespace());
	}

	public boolean requiresRemapping() {
		return !getRemappedOutput().exists() || inputFile.lastModified() <= 0 || inputFile.lastModified() > getRemappedOutput().lastModified() || forceRemap || !getRemappedPom().exists() || outputHasInvalidAccessWidener();
	}

	public void finaliseRemapping() {
		getRemappedOutput().setLastModified(inputFile.lastModified());
		savePom();

		// Validate that the remapped AW is what we want.
		if (outputHasInvalidAccessWidener()) {
			throw new RuntimeException("Failed to validate remapped access widener in " + getRemappedOutput());
		}
	}

	private void savePom() {
		try {
			String pomTemplate;

			try (InputStream input = ModDependencyInfo.class.getClassLoader().getResourceAsStream("mod_compile_template.pom")) {
				pomTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			}

			pomTemplate = pomTemplate
					.replace("%GROUP%", getGroup())
					.replace("%NAME%", name)
					.replace("%VERSION%", version);

			FileUtils.writeStringToFile(getRemappedPom(), pomTemplate, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write mod pom", e);
		}
	}

	public void forceRemap() {
		forceRemap = true;
	}

	@Override
	public String toString() {
		return getRemappedNotation();
	}

	public boolean hasClassifier() {
		return classifier != null && !classifier.isEmpty();
	}

	@Nullable
	public AccessWidenerData getAccessWidenerData() {
		return accessWidenerData;
	}

	private static AccessWidenerData tryReadAccessWidenerData(Path inputJar) throws IOException {
		byte[] modJsonBytes = ZipUtils.unpack(inputJar, "fabric.mod.json");
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("accessWidener")) {
			return null;
		}

		String accessWidenerPath = jsonObject.get("accessWidener").getAsString();
		byte[] accessWidener = ZipUtils.unpack(inputJar, accessWidenerPath);
		AccessWidenerReader.Header header = AccessWidenerReader.readHeader(accessWidener);

		return new AccessWidenerData(accessWidenerPath, header, accessWidener);
	}

	public record AccessWidenerData(String path, AccessWidenerReader.Header header, byte[] content) {
	}
}
