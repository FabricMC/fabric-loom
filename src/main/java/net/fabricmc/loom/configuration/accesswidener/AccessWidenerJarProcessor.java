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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.google.common.hash.Hashing;
import org.gradle.api.Project;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.ZipUtils;

public class AccessWidenerJarProcessor implements JarProcessor {
	// Filename used to store hash of input access widener in processed jar file
	private static final String HASH_FILENAME = "aw.sha256";
	private final AccessWidener accessWidener = new AccessWidener();
	private final Project project;
	// This is a SHA256 hash across the mod's and all transitive AWs
	private byte[] inputHash;

	public AccessWidenerJarProcessor(ConfigContext configContext) {
		this.project = configContext.project();
	}

	@Override
	public String getId() {
		return "loom:access_widener:" + Checksum.toHex(inputHash);
	}

	@Override
	public void setup() {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path awPath = extension.getAccessWidenerPath().get().getAsFile().toPath();

		// Read our own mod's access widener, used later for producing a version remapped to intermediary
		// The mod's own access widener file
		byte[] modAccessWidener;

		try {
			modAccessWidener = Files.readAllBytes(awPath);
		} catch (NoSuchFileException e) {
			throw new RuntimeException("Could not find access widener file @ " + awPath.toAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to read access widener: " + awPath);
		}

		AccessWidenerReader reader = new AccessWidenerReader(accessWidener);
		reader.read(modAccessWidener);

		inputHash = Hashing.sha256().hashBytes(modAccessWidener).asBytes();
	}

	@Override
	public void process(File file) {
		AccessWidenerTransformer applier = new AccessWidenerTransformer(project.getLogger(), accessWidener);
		applier.apply(file);

		try {
			ZipUtils.add(file.toPath(), HASH_FILENAME, inputHash);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write aw jar hash", e);
		}
	}
}
