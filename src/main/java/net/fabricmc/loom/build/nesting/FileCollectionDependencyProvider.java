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

package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.zip.ZipFile;

import com.google.common.base.Preconditions;
import org.gradle.api.file.FileCollection;

public final class FileCollectionDependencyProvider implements NestedJarProvider {
	private final Set<File> files;

	public FileCollectionDependencyProvider(FileCollection fileCollection) {
		this.files = resolve(fileCollection);
	}

	private Set<File> resolve(FileCollection fileCollection) {
		return fileCollection.getFiles();
	}

	@Override
	public Collection<File> provide() {
		validateFiles();
		return files;
	}

	private void validateFiles() {
		for (File file : files) {
			Preconditions.checkArgument(file.getName().endsWith(".jar"), String.format("Tried to nest %s but it is not a jar", file.getAbsolutePath()));
			Preconditions.checkArgument(file.exists(), String.format("Tried to nest jar %s but it does not exist", file.getAbsolutePath()));
			Preconditions.checkArgument(isMod(file), String.format("Cannot use a file collection to nest none mod jar %s", file.getAbsolutePath()));
		}
	}

	private static boolean isMod(File input) {
		try (ZipFile zipFile = new ZipFile(input)) {
			return zipFile.getEntry("fabric.mod.json") != null;
		} catch (IOException e) {
			return false;
		}
	}
}
