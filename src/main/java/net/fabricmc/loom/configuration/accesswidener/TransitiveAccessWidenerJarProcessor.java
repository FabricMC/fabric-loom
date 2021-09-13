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
import java.util.Arrays;

import com.google.common.hash.Hashing;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.accesswidener.ForwardingVisitor;
import net.fabricmc.accesswidener.TransitiveOnlyFilter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * Applies transitive access wideners that are inherited from mod and api dependencies.
 */
public class TransitiveAccessWidenerJarProcessor implements JarProcessor {
	// Filename used to store hash of input access widener in processed jar file
	public static final String HASH_FILENAME = "taw.sha256";
	// The collated AWs from other mods
	private final AccessWidener accessWidener = new AccessWidener();
	private final Project project;
	// This is a SHA256 hash across all transitive AWs, may be null in case there are no rules
	@Nullable
	private byte[] inputHash;

	public TransitiveAccessWidenerJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public String getId() {
		return "loom:transitive_access_wideners";
	}

	@Override
	public void setup() {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		// Write a collated full access widener for hashing it, and forward it to the access widener we'll apply
		AccessWidenerWriter fullWriter = new AccessWidenerWriter();
		ForwardingVisitor forwardingVisitor = new ForwardingVisitor(fullWriter, accessWidener);

		readTransitiveWidenersFromMods(extension, forwardingVisitor);

		if (!isEmpty()) {
			inputHash = Hashing.sha256().hashBytes(fullWriter.write()).asBytes();
		}
	}

	private boolean isEmpty() {
		return accessWidener.getTargets().isEmpty();
	}

	private void readTransitiveWidenersFromMods(LoomGradleExtension extension, AccessWidenerVisitor visitor) {
		// For other mods, only consider transitive AWs and remap from intermediary->named
		TinyRemapper tinyRemapper;

		try {
			tinyRemapper = TinyRemapperHelper.getTinyRemapper(project, "intermediary", "named");
		} catch (IOException e) {
			throw new RuntimeException("Failed to create tiny remapper for intermediary->named", e);
		}

		try {
			tinyRemapper.readClassPath(TinyRemapperHelper.getRemapClasspath(project));

			AccessWidenerRemapper remappingVisitor = new AccessWidenerRemapper(visitor, tinyRemapper.getRemapper(), "intermediary", "named");
			AccessWidenerReader transitiveReader = new AccessWidenerReader(new TransitiveOnlyFilter(remappingVisitor));

			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				// Only apply global AWs from mods that are part of the compile classpath
				if (!entry.compileClasspath()) {
					continue;
				}

				extension.getLazyConfigurationProvider(entry.sourceConfiguration()).configure(remappedConfig -> {
					for (File artifact : remappedConfig.resolve()) {
						AccessWidenerFile file = AccessWidenerFile.fromModJar(artifact.toPath());

						if (file != null) {
							project.getLogger().info("Reading transitive access widener from {}", file.modId());
							transitiveReader.read(file.content());
						}
					}
				});
			}
		} finally {
			tinyRemapper.finish();
		}
	}

	@Override
	public void process(File file) {
		if (isEmpty()) {
			project.getLogger().debug("Not applying transitive access wideners, since no rules are loaded");
			return;
		}

		AccessWidenerTransformer transformer = new AccessWidenerTransformer(project.getLogger(), accessWidener);
		transformer.apply(file);
		ZipUtil.addEntry(file, HASH_FILENAME, inputHash);
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, HASH_FILENAME);

		if (hash == null) {
			// If there's no hash file, that is in line with not having any access-widener rules
			return !isEmpty();
		}

		return !Arrays.equals(inputHash, hash); // TODO how do we know if the current jar as the correct access applied? save the hash of the input?
	}
}
