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
import java.io.FileReader;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.util.GradleSupport;

@SuppressWarnings("UnstableApiUsage")
public class SrgRemapJarTask extends Jar {
	private final RegularFileProperty input;
	private final RegularFileProperty mappings;

	public SrgRemapJarTask() {
		super();
		input = GradleSupport.getfileProperty(getProject());
		mappings = GradleSupport.getfileProperty(getProject());
	}

	@TaskAction
	public void doTask() throws Throwable {
		try (TSrgReader reader = new TSrgReader(new FileReader(mappings.getAsFile().get()));
				Atlas atlas = new Atlas()) {
			MappingSet mappings = reader.read();

			atlas.install(ctx -> new JarEntryRemappingTransformer(
					new LorenzRemapper(mappings, ctx.inheritanceProvider())
			));

			for (File file : getProject().getConfigurations().getByName("runtimeClasspath")) {
				atlas.use(file.toPath());
			}

			atlas.run(input.getAsFile().get().toPath(), getArchivePath().toPath());
		}
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}

	@InputFile
	public RegularFileProperty getMappings() {
		return mappings;
	}
}
