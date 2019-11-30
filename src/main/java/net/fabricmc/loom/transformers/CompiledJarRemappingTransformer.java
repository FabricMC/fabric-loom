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

package net.fabricmc.loom.transformers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.zeroturnaround.zip.commons.FileUtils;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.Project;
import org.gradle.api.tasks.CompileClasspath;
import org.apache.tools.ant.util.StreamUtils;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.transformers.parameters.ProjectReferencingParameters;
import net.fabricmc.loom.util.FabricModUtils;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

@CacheableTransform
public abstract class CompiledJarRemappingTransformer implements TransformAction<ProjectReferencingParameters> {
	@CompileClasspath
	@InputArtifact
	public abstract Provider<FileSystemLocation> getInput();

	@CompileClasspath
	@InputArtifactDependencies
	abstract FileCollection getDependencies();

	@Override
	public void transform(final TransformOutputs outputs) {
		final FileSystemLocation resolvedInput = getInput().getOrNull();
		if (resolvedInput == null) {
			lifecycle("Can not transform a jar who has no input.");
			return;
		}

		final File inputFile = resolvedInput.getAsFile();
		if (!inputFile.exists()) {
			lifecycle("Can not transform jar who does not exists on disk.");
			return;
		}

		if (!FabricModUtils.isFabricMod(
						getProject(),
						getProject().getLogger(),
						inputFile,
						inputFile.getName()
		)) {
			final File outputFile = outputs.file("not-remapped" + File.separator + inputFile.getName());
			try {
				FileUtils.copyFile(inputFile, outputFile);
			} catch (IOException e) {
				lifecycle("Failed to copy not remappable file to output.", e);
			}
            return;
		}

		final File outputFile = outputs.file("remapped" + File.separator + inputFile.getName());
		try {
			this.remapJar(inputFile, outputFile);
		} catch (IOException e) {
			lifecycle("Failed to remap file.", e);
		}
	}

	/**
	 * Short circuit method to handle logging a lifecycle message to the projects logger.
	 *
	 * @param message The message to log, formatted.
	 * @param objects The formatting parameters.
	 */
	private void lifecycle(String message, Object... objects) {
		getProject().getLogger().lifecycle("[ContainedZipStripping]: " + message, objects);
	}

	/**
	 * Short circuit method to handle logging a lifecycle message to the projects logger.
	 *
	 * @param message   The message to log, formatted.
	 * @param exception The exception to log.
	 */
	private void lifecycle(String message, Throwable exception) {
		getProject().getLogger().lifecycle("[ContainedZipStripping]: " + message, exception);
	}

	/**
	 * Returns the project that is referenced by the parameters.
	 *
	 * @return The project that this transformer is operating on.
	 */
	private Project getProject() {
		return TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get());
	}

	/**
	 * Remaps the input file to the output file with the information stored in the project.
	 *
	 * @param input  The input file.
	 * @param output The output file.
	 * @throws IOException Thrown when the operation failed.
	 */
	private void remapJar(File input, File output) throws IOException {
		final Project project = getProject();

		final LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		final String inputMappingName = "intermediary";
		final String outputMappingName = "named";

		final MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		final MappingsProvider mappingsProvider = extension.getMappingsProvider();

		final Path inputPath = input.getAbsoluteFile().toPath();
		final Path mc = mappedProvider.MINECRAFT_INTERMEDIARY_JAR.toPath();
		final Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);

		final TinyRemapper remapper = TinyRemapper.newRemapper()
													  .withMappings(
																	  TinyRemapperMappingsHelper.create(
																					  mappingsProvider.getMappings(),
																					  inputMappingName,
																					  outputMappingName,
																					  false
																	  )
													  )
													  .build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(Paths.get(output.getAbsolutePath())).build()) {
			outputConsumer.addNonClassFiles(inputPath);
			remapper.readClassPath(StreamUtils.iteratorAsStream(getDependencies().iterator()).map(File::toPath).toArray(Path[]::new));
			remapper.readClassPath(mc);
			remapper.readClassPath(mcDeps);
			remapper.readInputs(inputPath);
			remapper.apply(outputConsumer);
		} finally {
			remapper.finish();
		}

		if (!output.exists()) {
			throw new IOException("Failed to remap JAR to " + outputMappingName + " file not found: " + output.getAbsolutePath());
		}
	}
}
