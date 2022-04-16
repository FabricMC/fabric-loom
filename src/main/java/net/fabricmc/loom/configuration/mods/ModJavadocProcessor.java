/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class ModJavadocProcessor implements GenerateSourcesTask.MappingsProcessor {
	private final List<ModJavadoc> javadocs;

	private ModJavadocProcessor(List<ModJavadoc> javadocs) {
		this.javadocs = javadocs;
	}

	@Nullable
	public static ModJavadocProcessor create(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final List<ModJavadoc> javadocs = new ArrayList<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			Set<File> artifacts = extension.getLazyConfigurationProvider(entry.sourceConfiguration())
					.get()
					.resolve();

			for (File artifact : artifacts) {
				if (!ModUtils.isMod(artifact.toPath())) {
					continue;
				}

				final ModJavadoc modJavadoc;

				try {
					modJavadoc = ModJavadoc.fromModJar(artifact.toPath());
				} catch (IOException e) {
					throw new UncheckedIOException("Failed to read mod jar (%s)".formatted(artifact), e);
				}

				if (modJavadoc != null) {
					javadocs.add(modJavadoc);
				}
			}
		}

		if (javadocs.isEmpty()) {
			return null;
		}

		return new ModJavadocProcessor(javadocs);
	}

	@Override
	public boolean transform(MemoryMappingTree mappings) {
		for (ModJavadoc javadoc : javadocs) {
			try {
				// TODO I think this overrides existing comments, might need to get a bit more creative.
				javadoc.mappingTree().accept(new ForwardingJavadocMappingVisitor(mappings, javadoc.modId()));
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to apply javadoc from mod (%s)".formatted(javadoc.modId()), e);
			}
		}

		return true;
	}

	public record ModJavadoc(String modId, MemoryMappingTree mappingTree) {
		@Nullable
		public static ModJavadoc fromModJar(Path path) throws IOException {
			JsonObject jsonObject = ModUtils.getFabricModJson(path);

			if (jsonObject == null || !jsonObject.has("custom")) {
				return null;
			}

			final String modId = jsonObject.get("id").getAsString();
			final JsonObject custom = jsonObject.getAsJsonObject("custom");

			if (!custom.has(Constants.CustomModJsonKeys.PROVIDED_JAVADOC)) {
				return null;
			}

			final String javaDocPath = custom.getAsJsonPrimitive(Constants.CustomModJsonKeys.PROVIDED_JAVADOC).getAsString();
			final byte[] data = ZipUtils.unpack(path, javaDocPath);
			final MemoryMappingTree mappings = new MemoryMappingTree();

			try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data))) {
				MappingReader.read(reader, mappings);
			}

			if (!mappings.getSrcNamespace().equals(MappingsNamespace.INTERMEDIARY.toString())) {
				throw new IllegalStateException("Javadoc provided by mod (%s) must be have an intermediary source namespace".formatted(modId));
			}

			return new ModJavadoc(modId, mappings);
		}
	}

	// Ensure the mappings don't try to change names.
	private static final class ForwardingJavadocMappingVisitor extends ForwardingMappingVisitor {
		private final String modId;

		ForwardingJavadocMappingVisitor(MappingVisitor next, String modId) {
			super(next);
			this.modId = modId;
		}

		@Override
		public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
			throw new UnsupportedOperationException("Javadoc provided from mod (%s) attempted to map dst name".formatted(modId));
		}
	}
}
