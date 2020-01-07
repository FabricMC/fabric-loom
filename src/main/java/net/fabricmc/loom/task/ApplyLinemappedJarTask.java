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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;

public class ApplyLinemappedJarTask extends AbstractLoomTask {
	private Object mappedJar;
	private Object linemappedJar;

	@InputFile
	public File getMappedJar() {
		return getProject().file(mappedJar);
	}

	@OutputFile
	public File getLinemappedJar() {
		return getProject().file(linemappedJar);
	}

	public void setMappedJar(Object mappedJar) {
		this.mappedJar = mappedJar;
	}

	public void setLinemappedJar(Object linemappedJar) {
		this.linemappedJar = linemappedJar;
	}

	public static Path jarsBeforeLinemapping(LoomGradleExtension extension) throws IOException {
		Path path = Paths.get(extension.getUserCache().getPath(), "jarsBeforeLinemapping");
		Files.createDirectories(path);
		return path;
	}

	@TaskAction
	public void run() {
		Path mappedJarPath = getMappedJar().toPath();
		Path linemappedJarPath = getLinemappedJar().toPath();

		if (Files.exists(linemappedJarPath)) {
			try {
				Files.createDirectories(jarsBeforeLinemapping(getExtension()));
				Path notLinemappedJar = jarsBeforeLinemapping(getExtension()).resolve(mappedJarPath.getFileName());
				// The original jars without the linemapping needs to be saved for incremental decompilation
				if (!Files.exists(notLinemappedJar)) Files.copy(mappedJarPath, notLinemappedJar);
				Files.copy(linemappedJarPath, mappedJarPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
