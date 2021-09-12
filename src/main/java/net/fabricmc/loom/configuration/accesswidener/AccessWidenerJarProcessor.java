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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
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

public class AccessWidenerJarProcessor implements JarProcessor {
	// The mod's own access widener file
	private byte[] modAccessWidener;
	private final AccessWidener accessWidener = new AccessWidener();
	private final Project project;
	// This is a SHA256 hash across the mod's and all transitive AWs
	private byte[] inputHash;

	public AccessWidenerJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public String getId() {
		return "loom:access_widener";
	}

	@Override
	public void setup() {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path awPath = extension.getAccessWidenerPath().get().getAsFile().toPath();

		// Read our own mod's access widener, used later for producing a version remapped to intermediary
		try {
			modAccessWidener = Files.readAllBytes(awPath);
		} catch (NoSuchFileException e) {
			throw new RuntimeException("Could not find access widener file @ " + awPath.toAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to read access widener: " + awPath);
		}

		// Write a collated full access widener for hashing it, and forward it to the access widener we'll apply
		AccessWidenerWriter fullWriter = new AccessWidenerWriter();
		ForwardingVisitor forwardingVisitor = new ForwardingVisitor(fullWriter, accessWidener);

		// For our own mod, we include local AWs, and enable strict parsing
		AccessWidenerReader ownModReader = new AccessWidenerReader(forwardingVisitor);
		ownModReader.read(modAccessWidener);

		readTransitiveWidenersFromMods(extension, forwardingVisitor);

		inputHash = Hashing.sha256().hashBytes(fullWriter.write()).asBytes();
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
			AccessWidenerReader globalReader = new AccessWidenerReader(new TransitiveOnlyFilter(remappingVisitor));

			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				// Only apply global AWs from mods that are part of the compile classpath
				if (!entry.compileClasspath()) {
					continue;
				}

				extension.getLazyConfigurationProvider(entry.sourceConfiguration()).configure(remappedConfig -> {
					for (ResolvedArtifact artifact : remappedConfig.getResolvedConfiguration().getResolvedArtifacts()) {
						String modAwPath = getAccessWidenerPath(artifact.getFile().toPath());

						if (modAwPath == null) {
							continue;
						}

						globalReader.read(ZipUtil.unpackEntry(artifact.getFile(), modAwPath));
					}
				});
			}
		} finally {
			tinyRemapper.finish();
		}
	}

	@Override
	public void process(File file) {
		project.getLogger().lifecycle("Processing file: " + file.getName());
		ZipUtil.transformEntries(file, getTransformers(accessWidener.getTargets()));
		ZipUtil.addEntry(file, "aw.sha256", inputHash);
	}

	private ZipEntryTransformerEntry[] getTransformers(Set<String> classes) {
		return classes.stream()
				.map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);
				ClassVisitor classVisitor = AccessWidenerClassVisitor.createClassVisitor(Constants.ASM_VERSION, writer, accessWidener);

				project.getLogger().lifecycle("Applying access widener to " + className);

				reader.accept(classVisitor, 0);
				return writer.toByteArray();
			}
		};
	}

	/**
	 * Get this mods access widener remapped to the intermediary namespace.
	 */
	public byte[] getRemappedAccessWidener(Remapper asmRemapper, String targetNamespace) throws IOException {
		int version = AccessWidenerReader.readVersion(modAccessWidener);

		AccessWidenerWriter writer = new AccessWidenerWriter(version);
		AccessWidenerRemapper remapper = new AccessWidenerRemapper(writer, asmRemapper, "named", targetNamespace);
		AccessWidenerReader reader = new AccessWidenerReader(remapper);
		reader.read(modAccessWidener);

		return writer.write();
	}

	public String getAccessWidenerPath(Path modJarPath) {
		byte[] modJsonBytes = ZipUtil.unpackEntry(modJarPath.toFile(), "fabric.mod.json");

		if (modJsonBytes == null) {
			return null;
		}

		JsonObject jsonObject = new Gson().fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("accessWidener")) {
			return null;
		}

		return jsonObject.get("accessWidener").getAsString();
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, "aw.sha256");

		if (hash == null) {
			return true;
		}

		return !Arrays.equals(inputHash, hash); // TODO how do we know if the current jar as the correct access applied? save the hash of the input?
	}
}
