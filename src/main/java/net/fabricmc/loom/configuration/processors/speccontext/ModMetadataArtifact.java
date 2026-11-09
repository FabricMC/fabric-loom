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
import java.util.Date;
import java.util.Set;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.TaskDependency;

/// Publishes an existing metadata file without encoding its contents or retaining Gradle project objects.
/// A component exposes its mod JSON and only the CT/AW and Javadoc files referenced by that JSON.
/// Resource classifiers preserve their logical paths so [ModMetadataResolver] can reconstruct
/// the mod's source without knowing the producer's resource directories. No producer tasks are required.
///
/// @param file the mod JSON file or a referenced resource file
/// @param type the file's role: mod JSON, class tweaker, or provided Javadoc
/// @param classifier `metadata` for the JSON, or the resource path used in the mod JSON
record ModMetadataArtifact(File file, ModMetadataArtifactType type, String classifier) implements PublishArtifact {
	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public String getExtension() {
		return "";
	}

	@Override
	public String getType() {
		return type.value();
	}

	@Override
	public String getClassifier() {
		return classifier;
	}

	@Override
	public File getFile() {
		return file;
	}

	@Override
	public Date getDate() {
		return new Date(0);
	}

	@Override
	public TaskDependency getBuildDependencies() {
		return task -> Set.of();
	}
}
