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

package net.fabricmc.loom.configuration.providers.mappings.utils;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.util.Checksum;

public class LocalFileSpec implements FileSpec {
	private final File file;
	private final int hash;

	public LocalFileSpec(File file) {
		this.file = file;
		this.hash = calculateHashCode();
	}

	private int calculateHashCode() {
		if (!file.exists()) {
			throw new RuntimeException("Could not find %s, it must be present at spec creation time to calculate mappings hash".formatted(file.getAbsolutePath()));
		}

		// Use the file hash as part of the spec, this means if the input file changes the mappings will be re-generated.
		return Objects.hash(Arrays.hashCode(Checksum.sha256(file)), file.getAbsolutePath());
	}

	@Override
	public Path get(MappingContext context) {
		return file.toPath();
	}

	@Override
	public int hashCode() {
		return hash;
	}
}
