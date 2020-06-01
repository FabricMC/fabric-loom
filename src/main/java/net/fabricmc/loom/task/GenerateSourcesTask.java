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
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.loom.util.progress.ProgressLogger;
import net.fabricmc.stitch.util.StitchUtil;

public class GenerateSourcesTask extends AbstractLoomTask {
	public final LoomDecompiler decompiler;

	@Inject
	public GenerateSourcesTask(LoomDecompiler decompiler) {
		this.decompiler = decompiler;

		setGroup("fabric");
		getOutputs().upToDateWhen((o) -> false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		int threads = Runtime.getRuntime().availableProcessors();
		Path javaDocs = getExtension().getMappingsProvider().tinyMappings.toPath();
		Collection<Path> libraries = getExtension().getMinecraftProvider().getLibraryProvider().getLibraries()
						.stream().map(File::toPath).collect(Collectors.toSet());

		DecompilationMetadata metadata = new DecompilationMetadata(threads, javaDocs, libraries);
		Path compiledJar = getExtension().getMappingsProvider().mappedProvider.getMappedJar().toPath();
		Path sourcesDestination = LoomGradlePlugin.getMappedByproduct(getProject(), "-sources.jar").toPath();
		Path linemap = LoomGradlePlugin.getMappedByproduct(getProject(), "-sources.lmap").toPath();
		decompiler.decompile(compiledJar, sourcesDestination, linemap, metadata);

		if (Files.exists(linemap)) {
			Path linemappedJarDestination = LoomGradlePlugin.getMappedByproduct(getProject(), "-linemapped.jar").toPath();

			remapLineNumbers(compiledJar, linemap, linemappedJarDestination);

			// In order for IDEs to recognize the new line mappings, we need to overwrite the existing compiled jar
			// with the linemapped one. In the name of not destroying the existing jar, we will copy it to somewhere else.
			Path unlinemappedJar = LoomGradlePlugin.getMappedByproduct(getProject(), "-unlinemapped.jar").toPath();

			// The second time genSources is ran, we want to keep the existing unlinemapped jar.
			if (!Files.exists(unlinemappedJar)) {
				Files.copy(compiledJar, unlinemappedJar);
			}

			Files.copy(linemappedJarDestination, compiledJar, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(linemappedJarDestination);
		}
	}

	private void remapLineNumbers(Path oldCompiledJar, Path linemap, Path linemappedJarDestination) throws IOException {
		getProject().getLogger().info(":adjusting line numbers");
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemap.toFile());

		ProgressLogger progressLogger = net.fabricmc.loom.util.progress.ProgressLogger.getProgressFactory(getProject(), getClass().getName());
		progressLogger.start("Adjusting line numbers", "linemap");

		try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(oldCompiledJar.toFile(), true);
			StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(linemappedJarDestination.toFile(), true)) {
			remapper.process(progressLogger, inFs.get().getPath("/"), outFs.get().getPath("/"));
		}

		progressLogger.completed();
	}
}
