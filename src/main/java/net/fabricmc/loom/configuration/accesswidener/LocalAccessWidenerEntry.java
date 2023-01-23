/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.LazyCloseable;
import net.fabricmc.loom.util.fmj.ModEnvironment;
import net.fabricmc.tinyremapper.TinyRemapper;

public record LocalAccessWidenerEntry(Path path, String hash) implements AccessWidenerEntry {
	public static LocalAccessWidenerEntry create(Path path) {
		try {
			return new LocalAccessWidenerEntry(path, Checksum.sha1Hex(path));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create LocalAccessWidenerEntry", e);
		}
	}

	@Override
	public void read(AccessWidenerVisitor visitor, LazyCloseable<TinyRemapper> remapper) throws IOException {
		var reader = new AccessWidenerReader(visitor);
		reader.read(Files.readAllBytes(path));
	}

	@Override
	public ModEnvironment environment() {
		return ModEnvironment.UNIVERSAL;
	}

	@Override
	public @Nullable String mappingId() {
		return null;
	}

	@Override
	public String getSortKey() {
		return "local";
	}

	@Override
	public int hashCode() {
		return hash.hashCode();
	}
}
