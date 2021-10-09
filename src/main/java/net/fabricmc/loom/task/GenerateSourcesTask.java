/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.accesswidener.TransitiveAccessWidenerMappingsProcessor;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.gradle.ProgressLogger;

public class GenerateSourcesTask extends AbstractLoomTask {
	public final LoomDecompiler decompiler;

	private File inputJar;

	@Inject
	public GenerateSourcesTask(LoomDecompiler decompiler) {
		this.decompiler = decompiler;

		getOutputs().upToDateWhen((o) -> false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		int threads = Runtime.getRuntime().availableProcessors();
		Collection<Path> libraries = getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).collect(Collectors.toSet());

		DecompilationMetadata metadata = new DecompilationMetadata(threads, getMappings(), libraries);
		Path runtimeJar = getExtension().getMappingsProvider().mappedProvider.getMappedJar().toPath();
		Path sourcesDestination = getMappedJarFileWithSuffix("-sources.jar").toPath();
		Path linemap = getMappedJarFileWithSuffix("-sources.lmap").toPath();
		decompiler.decompile(inputJar.toPath(), sourcesDestination, linemap, metadata);

		if (Files.exists(linemap)) {
			Path linemappedJarDestination = getMappedJarFileWithSuffix("-linemapped.jar").toPath();

			// Line map the actually jar used to run the game, not the one used to decompile
			remapLineNumbers(runtimeJar, linemap, linemappedJarDestination);

			Files.copy(linemappedJarDestination, runtimeJar, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(linemappedJarDestination);
		}
	}

	private void remapLineNumbers(Path oldCompiledJar, Path linemap, Path linemappedJarDestination) throws IOException {
		getProject().getLogger().info(":adjusting line numbers");
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemap.toFile());

		ProgressLogger progressLogger = ProgressLogger.getProgressFactory(getProject(), getClass().getName());
		progressLogger.start("Adjusting line numbers", "linemap");

		try (FileSystemUtil.Delegate inFs = FileSystemUtil.getJarFileSystem(oldCompiledJar, true);
				FileSystemUtil.Delegate outFs = FileSystemUtil.getJarFileSystem(linemappedJarDestination, true)) {
			remapper.process(progressLogger, inFs.get().getPath("/"), outFs.get().getPath("/"));
		}

		progressLogger.completed();
	}

	private File getMappedJarFileWithSuffix(String suffix) {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		String path = mappedJar.getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	private Path getMappings() {
		Path baseMappings = getExtension().getMappingsProvider().tinyMappings;

		if (getExtension().getEnableTransitiveAccessWideners().get()) {
			List<AccessWidenerFile> accessWideners = getExtension().getTransitiveAccessWideners();

			if (accessWideners.isEmpty()) {
				return baseMappings;
			}

			Path outputMappings;

			try {
				outputMappings = Files.createTempFile("loom-transitive-mappings", ".tiny");
			} catch (IOException e) {
				throw new RuntimeException("Failed to create temp file", e);
			}

			TransitiveAccessWidenerMappingsProcessor.process(baseMappings, outputMappings, accessWideners, getProject().getLogger());

			return outputMappings;
		}

		return baseMappings;
	}

	@InputFile
	public File getInputJar() {
		return inputJar;
	}

	public GenerateSourcesTask setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}
}
