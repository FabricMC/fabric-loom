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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class ModJavadocProcessor implements JarProcessor, GenerateSourcesTask.MappingsProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModJavadocProcessor.class);

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
			javadoc.apply(mappings);
		}

		return true;
	}

	@Override
	public String getId() {
		return "loom:interface_injection:" + javadocs.hashCode();
	}

	@Override
	public void setup() {
	}

	@Override
	public void process(File file) {
		// No need to actually process anything, we need to be a JarProcessor to ensure that the jar is cached correctly.
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

			if (!mappings.getDstNamespaces().isEmpty()) {
				throw new IllegalStateException("Javadoc provided by mod (%s) must not contain any dst names".formatted(modId));
			}

			return new ModJavadoc(modId, mappings);
		}

		public void apply(MemoryMappingTree target) {
			if (!mappingTree.getSrcNamespace().equals(target.getSrcNamespace())) {
				throw new IllegalStateException("Cannot apply mappings to differing namespaces. source: %s target: %s".formatted(mappingTree.getSrcNamespace(), target.getSrcNamespace()));
			}

			for (MappingTree.ClassMapping sourceClass : mappingTree.getClasses()) {
				final MappingTree.ClassMapping targetClass = target.getClass(sourceClass.getSrcName());

				if (targetClass == null) {
					LOGGER.warn("Could not find provided javadoc target class {} from mod {}", sourceClass.getSrcName(), modId);
					continue;
				}

				applyComment(sourceClass, targetClass);

				for (MappingTree.FieldMapping sourceField : sourceClass.getFields()) {
					final MappingTree.FieldMapping targetField = targetClass.getField(sourceField.getSrcName(), sourceField.getSrcDesc());

					if (targetField == null) {
						LOGGER.warn("Could not find provided javadoc target field {}{} from mod {}", sourceField.getSrcName(), sourceField.getSrcDesc(), modId);
						continue;
					}

					applyComment(sourceField, targetField);
				}

				for (MappingTree.MethodMapping sourceMethod : sourceClass.getMethods()) {
					final MappingTree.MethodMapping targetMethod = targetClass.getMethod(sourceMethod.getSrcName(), sourceMethod.getSrcDesc());

					if (targetMethod == null) {
						LOGGER.warn("Could not find provided javadoc target method {}{} from mod {}", sourceMethod.getSrcName(), sourceMethod.getSrcDesc(), modId);
						continue;
					}

					applyComment(sourceMethod, targetMethod);
				}
			}
		}

		private <T extends MappingTree.ElementMapping> void applyComment(T source, T target) {
			String sourceComment = source.getComment();

			if (sourceComment == null) {
				LOGGER.warn("Mod {} provided javadoc has mapping for {}, without comment", modId, source);
				return;
			}

			String targetComment = target.getComment();

			if (targetComment == null) {
				targetComment = "";
			} else {
				targetComment += "\n";
			}

			targetComment += sourceComment;
			target.setComment(targetComment);
		}
	}
}
