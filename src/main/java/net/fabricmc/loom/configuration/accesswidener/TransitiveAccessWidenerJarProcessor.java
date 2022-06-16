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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * Applies transitive access wideners that are inherited from mod and api dependencies.
 */
public class TransitiveAccessWidenerJarProcessor implements JarProcessor {
	private final Project project;
	private final LoomGradleExtension extension;
	private final AccessWidenerAdapter accessWidenerAdapter;

	private final List<ModAccessWidener> transitiveAccessWideners;

	public TransitiveAccessWidenerJarProcessor(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
		this.accessWidenerAdapter = AccessWidenerAdapter.get(project);

		transitiveAccessWideners = getTransitiveAccessWideners();

		extension.addTransitiveAccessWideners(transitiveAccessWideners);
	}

	@Override
	public void setup() {
	}

	public boolean isEmpty() {
		return transitiveAccessWideners.isEmpty();
	}

	@Override
	public String getId() {
		Preconditions.checkArgument(!isEmpty());

		return "loom:transitive_class_tweakers:" + transitiveAccessWideners.hashCode();
	}

	private List<ModAccessWidener> getTransitiveAccessWideners() {
		final List<ModAccessWidener> accessWideners = new ArrayList<>();
		final Set<Path> possibleModJars = new HashSet<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			// Only apply global AWs from mods that are part of the compile classpath
			if (!entry.compileClasspath()) {
				continue;
			}

			final Configuration configuration = extension.getLazyConfigurationProvider(entry.sourceConfiguration()).get();

			// Based off the logic in ModCompileRemapper.
			for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
				possibleModJars.add(artifact.getFile().toPath());
			}

			for (FileCollectionDependency dependency : configuration.getAllDependencies().withType(FileCollectionDependency.class)) {
				FileCollection files = dependency.getFiles();

				for (File artifact : files) {
					possibleModJars.add(artifact.toPath());
				}
			}
		}

		for (Path path : possibleModJars) {
			if (!Files.exists(path)) {
				project.getLogger().debug("Could not find transitive {} in {} as it does not exist", accessWidenerAdapter.getName(), path.toAbsolutePath());
				continue;
			}

			ModAccessWidener tweaker = ModAccessWidener.fromModJar(path);

			if (tweaker == null) {
				continue;
			}

			if (!accessWidenerAdapter.isTransitive(tweaker)) {
				// AW does not contain anything transitive, skip over it
				continue;
			}

			accessWideners.add(tweaker);
		}

		return accessWideners;
	}

	@Override
	public void process(File file) {
		Preconditions.checkArgument(!isEmpty());

		// For other mods, only consider transitive AWs and remap from intermediary->named
		final TinyRemapper tinyRemapper = createTinyRemapper();

		try {
			accessWidenerAdapter.transformJar(file.toPath(), transitiveAccessWideners,
					tinyRemapper.getEnvironment().getRemapper(), MappingsNamespace.INTERMEDIARY.toString(),
					MappingsNamespace.NAMED.toString());
		} finally {
			tinyRemapper.finish();
		}
	}

	private TinyRemapper createTinyRemapper() {
		try {
			TinyRemapper tinyRemapper = TinyRemapperHelper.getTinyRemapper(project, "intermediary", "named");

			tinyRemapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(project));

			for (Path minecraftJar : extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
				tinyRemapper.readClassPath(minecraftJar);
			}

			return tinyRemapper;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create tiny remapper for intermediary->named", e);
		}
	}
}
