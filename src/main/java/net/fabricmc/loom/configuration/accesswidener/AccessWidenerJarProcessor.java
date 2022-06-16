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
import java.nio.file.Path;
import java.util.List;

import com.google.common.hash.Hashing;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Checksum;

public class AccessWidenerJarProcessor implements JarProcessor {
	// The mod's own access widener file
	private AccessWidenerProvider accessWidener;
	private final Project project;
	// This is a SHA256 hash across the mod's and all transitive AWs
	private byte[] inputHash;

	public AccessWidenerJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public String getId() {
		return "loom:access_widener:" + Checksum.toHex(inputHash);
	}

	@Override
	public void setup() {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path ctPath = extension.getAccessWidenerPath().get().getAsFile().toPath();

		// Read our own mod's access widener, used later for producing a version remapped to intermediary
		try {
			accessWidener = SimpleAccessWidenerProvider.fromPath(ctPath);
		} catch (Exception e) {
			throw new RuntimeException("Could not find %s file @ %s".formatted(AccessWidenerAdapter.get(project).getName(), ctPath.toAbsolutePath()));
		}

		inputHash = Hashing.sha256().hashBytes(accessWidener.getAccessWidener()).asBytes();
	}

	@Override
	public void process(File file) {
		AccessWidenerAdapter.get(project).transformJar(file.toPath(), List.of(accessWidener));
	}
}
