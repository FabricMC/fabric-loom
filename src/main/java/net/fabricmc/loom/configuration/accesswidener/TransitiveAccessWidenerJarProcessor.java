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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.TransitiveOnlyFilter;
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

	private final List<AccessWidenerFile> transitiveAccessWideners;

	public TransitiveAccessWidenerJarProcessor(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);

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

		return "loom:transitive_access_wideners:" + transitiveAccessWideners.hashCode();
	}

	private List<AccessWidenerFile> getTransitiveAccessWideners() {
		List<AccessWidenerFile> accessWideners = new ArrayList<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			// Only apply global AWs from mods that are part of the compile classpath
			if (!entry.compileClasspath()) {
				continue;
			}

			Set<File> artifacts = extension.getLazyConfigurationProvider(entry.sourceConfiguration())
					.get()
					.resolve();

			for (File artifact : artifacts) {
				AccessWidenerFile accessWidener = AccessWidenerFile.fromModJar(artifact.toPath());

				if (accessWidener == null) {
					continue;
				}

				if (!TransitiveDetectorVisitor.isTransitive(accessWidener.content())) {
					// AW does not contain anything transitive, skip over it
					continue;
				}

				accessWideners.add(accessWidener);
			}
		}

		return accessWideners;
	}

	@Override
	public void process(File file) {
		Preconditions.checkArgument(!isEmpty());

		AccessWidener accessWidener = createAccessWidener();
		AccessWidenerTransformer transformer = new AccessWidenerTransformer(project.getLogger(), accessWidener);
		transformer.apply(file);
	}

	private AccessWidener createAccessWidener() {
		AccessWidener accessWidener = new AccessWidener();
		// For other mods, only consider transitive AWs and remap from intermediary->named
		TinyRemapper tinyRemapper = createTinyRemapper();

		try {
			AccessWidenerRemapper remappingVisitor = new AccessWidenerRemapper(
					accessWidener,
					tinyRemapper.getRemapper(),
					MappingsNamespace.INTERMEDIARY.toString(),
					MappingsNamespace.NAMED.toString()
			);
			AccessWidenerReader transitiveReader = new AccessWidenerReader(new TransitiveOnlyFilter(remappingVisitor));

			for (AccessWidenerFile accessWidenerFile : transitiveAccessWideners) {
				project.getLogger().info("Reading transitive access widener from {}", accessWidenerFile.modId());
				transitiveReader.read(accessWidenerFile.content());
			}
		} finally {
			tinyRemapper.finish();
		}

		return accessWidener;
	}

	private TinyRemapper createTinyRemapper() {
		try {
			TinyRemapper tinyRemapper = TinyRemapperHelper.getTinyRemapper(project, "intermediary", "named");

			tinyRemapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(project));
			tinyRemapper.readClassPath(extension.getMinecraftMappedProvider().getIntermediaryJar().toPath());

			return tinyRemapper;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create tiny remapper for intermediary->named", e);
		}
	}

	@Override
	public boolean isInvalid(File file) {
		// The hash is handled by getId()
		return false;
	}

	private static class TransitiveDetectorVisitor implements AccessWidenerVisitor {
		private boolean transitive = false;

		@Override
		public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
			if (transitive) {
				this.transitive = true;
			}
		}

		@Override
		public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			if (transitive) {
				this.transitive = true;
			}
		}

		@Override
		public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
			if (transitive) {
				this.transitive = true;
			}
		}

		public static boolean isTransitive(byte[] content) {
			if (AccessWidenerReader.readVersion(content) < 2) {
				// Transitive AWs are only in v2 or higher, so we can save parsing the file to find out...
				return false;
			}

			TransitiveDetectorVisitor transitiveDetector = new TransitiveDetectorVisitor();
			new AccessWidenerReader(transitiveDetector).read(content);
			return transitiveDetector.transitive;
		}
	}
}
