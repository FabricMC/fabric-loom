/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.FileSystemUtil;

public class JarSplitter {
	final Path inputJar;

	public JarSplitter(Path inputJar) {
		this.inputJar = inputJar;
	}

	@Nullable
	public Target analyseTarget() {
		try (FileSystemUtil.Delegate input = FileSystemUtil.getJarFileSystem(inputJar)) {
			final Manifest manifest = input.fromInputStream(Manifest::new, AbstractRemapJarTask.MANIFEST_PATH);

			if (!Boolean.parseBoolean(manifest.getMainAttributes().getValue(AbstractRemapJarTask.MANIFEST_SPLIT_ENV_KEY))) {
				// Jar was not built with splitting enabled.
				return null;
			}

			final HashSet<String> clientEntries = new HashSet<>(readClientEntries(manifest));

			if (clientEntries.isEmpty()) {
				// No client entries.
				return Target.COMMON_ONLY;
			}

			final List<String> entries = new LinkedList<>();

			// Must collect all the input entries to see if this might be a client only jar.
			try (Stream<Path> walk = Files.walk(input.get().getPath("/"))) {
				final Iterator<Path> iterator = walk.iterator();

				while (iterator.hasNext()) {
					final Path entry = iterator.next();

					if (!Files.isRegularFile(entry)) {
						continue;
					}

					final Path relativePath = input.get().getPath("/").relativize(entry);

					if (relativePath.startsWith("META-INF")) {
						if (isSignatureData(relativePath)) {
							// Ignore any signature data
							continue;
						}

						if (relativePath.endsWith("MANIFEST.MF")) {
							// Ignore the manifest
							continue;
						}
					}

					entries.add(relativePath.toString());
				}
			}

			for (String entry : entries) {
				if (!clientEntries.contains(entry)) {
					// Found a common entry, we need to split,.
					return Target.SPLIT;
				}
			}

			// All input entries are client only entries.
			return Target.CLIENT_ONLY;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read jar", e);
		}
	}

	public boolean split(Path commonOutputJar, Path clientOutputJar) throws IOException {
		try (FileSystemUtil.Delegate input = FileSystemUtil.getJarFileSystem(inputJar)) {
			final Manifest manifest = input.fromInputStream(Manifest::new, AbstractRemapJarTask.MANIFEST_PATH);

			if (!Boolean.parseBoolean(manifest.getMainAttributes().getValue(AbstractRemapJarTask.MANIFEST_SPLIT_ENV_KEY))) {
				throw new UnsupportedOperationException("Cannot split jar that has not been built with a split env");
			}

			final List<String> clientEntries = readClientEntries(manifest);

			if (clientEntries.isEmpty()) {
				throw new IllegalStateException("Expected to split jar with no client entries");
			}

			try (FileSystemUtil.Delegate commonOutput = FileSystemUtil.getJarFileSystem(commonOutputJar, true);
					FileSystemUtil.Delegate clientOutput = FileSystemUtil.getJarFileSystem(clientOutputJar, true);
					Stream<Path> walk = Files.walk(input.get().getPath("/"))) {
				final Iterator<Path> iterator = walk.iterator();

				while (iterator.hasNext()) {
					final Path entry = iterator.next();

					if (!Files.isRegularFile(entry)) {
						continue;
					}

					final Path relativePath = input.get().getPath("/").relativize(entry);

					if (relativePath.startsWith("META-INF")) {
						if (isSignatureData(relativePath)) {
							// Strip any signature data
							continue;
						}
					}

					final String entryPath = relativePath.toString();

					/*
					Copy the manifest to both jars
					- Remove signature data
					- Remove split data as its already been split.
					 */
					if (entryPath.equals(AbstractRemapJarTask.MANIFEST_PATH)) {
						final Manifest outManifest = new Manifest(manifest);
						final Attributes attributes = outManifest.getMainAttributes();
						stripSignatureData(outManifest);

						attributes.remove(Attributes.Name.SIGNATURE_VERSION);
						Objects.requireNonNull(attributes.remove(AbstractRemapJarTask.MANIFEST_SPLIT_ENV_NAME));
						Objects.requireNonNull(attributes.remove(AbstractRemapJarTask.MANIFEST_CLIENT_ENTRIES_NAME));

						// TODO add an attribute to denote if the jar is common or client now

						final ByteArrayOutputStream out = new ByteArrayOutputStream();

						outManifest.write(out);
						final byte[] manifestBytes = out.toByteArray();

						writeBytes(manifestBytes, commonOutput.getPath(AbstractRemapJarTask.MANIFEST_PATH));
						writeBytes(manifestBytes, clientOutput.getPath(AbstractRemapJarTask.MANIFEST_PATH));

						continue;
					}

					final FileSystemUtil.Delegate target = clientEntries.contains(entryPath) ? clientOutput : commonOutput;
					final Path outputEntry = target.getPath(entryPath);
					final Path outputParent = outputEntry.getParent();

					if (outputParent != null) {
						Files.createDirectories(outputParent);
					}

					Files.copy(entry, outputEntry, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}

		return true;
	}

	private List<String> readClientEntries(Manifest manifest) {
		final Attributes attributes = manifest.getMainAttributes();
		final String clientEntriesValue = attributes.getValue(AbstractRemapJarTask.MANIFEST_CLIENT_ENTRIES_KEY);

		if (clientEntriesValue == null || clientEntriesValue.isBlank()) {
			return Collections.emptyList();
		}

		return Arrays.stream(clientEntriesValue.split(";")).toList();
	}

	private boolean isSignatureData(Path path) {
		final String fileName = path.getFileName().toString();
		return fileName.endsWith(".SF")
				|| fileName.endsWith(".DSA")
				|| fileName.endsWith(".RSA")
				|| fileName.startsWith("SIG-");
	}

	// Based off tiny-remapper's MetaInfFixer
	private static void stripSignatureData(Manifest manifest) {
		for (Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext(); ) {
			Attributes attrs = it.next();

			for (Iterator<Object> it2 = attrs.keySet().iterator(); it2.hasNext(); ) {
				Attributes.Name attrName = (Attributes.Name) it2.next();
				String name = attrName.toString();

				if (name.endsWith("-Digest") || name.contains("-Digest-") || name.equals("Magic")) {
					it2.remove();
				}
			}

			if (attrs.isEmpty()) it.remove();
		}
	}

	private static void writeBytes(byte[] bytes, Path path) throws IOException {
		final Path parent = path.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Files.write(path, bytes);
	}

	public enum Target {
		COMMON_ONLY(true, false),
		CLIENT_ONLY(false, true),
		SPLIT(true, true);

		final boolean common, client;

		Target(boolean common, boolean client) {
			this.common = common;
			this.client = client;
		}

		public boolean common() {
			return common;
		}

		public boolean client() {
			return client;
		}
	}
}
