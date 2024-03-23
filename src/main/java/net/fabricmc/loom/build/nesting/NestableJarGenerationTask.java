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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;

public abstract class NestableJarGenerationTask extends DefaultTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(IncludedJarFactory.class);
	private static final String SEMVER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
	private static final Pattern SEMVER_PATTERN = Pattern.compile(SEMVER_REGEX);

	@InputFiles
	@Incremental
	@PathSensitive(PathSensitivity.NAME_ONLY)
	protected abstract ConfigurableFileCollection getJars();

	@InputFiles
	@Incremental
	@PathSensitive(PathSensitivity.NAME_ONLY)
	protected abstract ConfigurableFileCollection getModJsons();

	@OutputDirectory
	protected abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	void makeNestableJars(InputChanges inputChanges) {
		Map<String, File> fabricModJsons = new HashMap<>();
		getModJsons().forEach(file -> {
			String name = file.getName();
			if (name.endsWith(".json")) {
				fabricModJsons.put(name.substring(0, name.length() - 5), file);
			}
		});
		if (!inputChanges.isIncremental()) {
			try {
				File targetDir = getOutputDirectory().get().getAsFile();
				FileUtils.deleteDirectory(targetDir);
				targetDir.mkdirs();
			} catch (IOException e) {
                    throw new org.gradle.api.UncheckedIOException(e);
            }
		}
		inputChanges.getFileChanges(getJars()).forEach(change -> {
			File targetFile = getOutputDirectory().file(change.getFile().getName()).get().getAsFile();
			targetFile.delete();
			if (change.getChangeType() != ChangeType.REMOVED) {
				File fabricModJson = Objects.requireNonNull(fabricModJsons.get(change.getFile().getName()), "Generated fabric.mod.json file is missing for dependency file "+change.getFile().getName());
				makeNestableJar(change.getFile(), targetFile, fabricModJson);
			}
		});
	}

	private void makeNestableJar(final File input, final File output, final File modJsonFile) {
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
			ZipReprocessorUtil.appendZipEntry(output.toPath(), "fabric.mod.json", FileUtils.readFileToByteArray(modJsonFile));
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
