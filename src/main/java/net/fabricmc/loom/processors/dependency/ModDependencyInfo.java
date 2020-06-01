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

package net.fabricmc.loom.processors.dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.JsonObject;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.loom.util.ModProcessor;

public class ModDependencyInfo {
	public final String group;
	public final String name;
	public final String version;
	public final String classifier;
	public final File inputFile;
	public final File sourcesFile;
	public final Configuration targetConfig;

	public final RemapData remapData;

	public ModDependencyInfo(String group, String name, String version, String classifier, File inputFile, File sourcesFile, Configuration targetConfig, RemapData remapData) {
		this.group = group;
		this.name = name;
		this.version = version;
		this.classifier = classifier;
		this.inputFile = inputFile;
		this.sourcesFile = sourcesFile;
		this.targetConfig = targetConfig;
		this.remapData = remapData;
	}

	public String getRemappedNotation() {
		return String.format("%s:%s:%s@%s%s", group, name, version, remapData.mappingsSuffix, classifier);
	}

	public String getRemappedFilename() {
		return String.format("%s-%s@%s", name, version, remapData.mappingsSuffix + classifier.replace(':', '-'));
	}

	public File getRemappedOutput() {
		return new File(remapData.modStore, getRemappedFilename() + ".jar");
	}

	public File getInputFile() {
		return inputFile;
	}

	public boolean requiresRemapping() {
		return !getRemappedOutput().exists() || inputFile.lastModified() <= 0 || inputFile.lastModified() > getRemappedOutput().lastModified();
	}

	public void finaliseRemapping() {
		getRemappedOutput().setLastModified(inputFile.lastModified());
	}

	@Override
	public String toString() {
		return String.format("%s:%s:%s:%s", group, name, version, classifier);
	}

	public String getAccessWidener() throws IOException {
		try (JarFile jarFile = new JarFile(getInputFile())) {
			JarEntry modJsonEntry = jarFile.getJarEntry("fabric.mod.json");

			if (modJsonEntry == null) {
				return null;
			}

			try (InputStream inputStream = jarFile.getInputStream(modJsonEntry)) {
				JsonObject json = ModProcessor.GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);

				if (!json.has("accessWidener")) {
					return null;
				}

				return json.get("accessWidener").getAsString();
			}
		}
	}
}
