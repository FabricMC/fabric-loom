/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2026 FabricMC
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

package net.fabricmc.loom.configuration.processors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class ModJavadocProcessor implements MinecraftJarProcessor<ModJavadocProcessor.Spec> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModJavadocProcessor.class);

	private final String name;

	@Inject
	public ModJavadocProcessor(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ModJavadocProcessor.@Nullable Spec buildSpec(SpecContext context) {
		List<ModJavadoc> javadocs = new ArrayList<>();

		for (FabricModJson fabricModJson : context.modDependenciesCompileRuntime()) {
			ModJavadoc javadoc = ModJavadoc.create(fabricModJson, context.productionNamespace());

			if (javadoc != null) {
				javadocs.add(javadoc);
			}
		}

		if (javadocs.isEmpty()) {
			return null;
		}

		javadocs.sort(Comparator.comparing(ModJavadoc::modId));
		return new Spec(Collections.unmodifiableList(javadocs));
	}

	public record Spec(List<ModJavadoc> javadocs) implements MinecraftJarProcessor.Spec {
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext context) {
		// Nothing to do for the jar
	}

	@Override
	public @Nullable MappingsProcessor<Spec> processMappings() {
		return (mappings, spec, context) -> {
			for (ModJavadoc javadoc : spec.javadocs()) {
				javadoc.apply(mappings, context.disableObfuscation());
			}

			return true;
		};
	}

	public record ModJavadoc(String modId, MemoryMappingTree mappingTree, String mappingsHash) {
		@Nullable
		public static ModJavadoc create(FabricModJson fabricModJson, MappingsNamespace productionNamespace) {
			final String modId = fabricModJson.getId();
			final JsonElement customElement = fabricModJson.getCustom(Constants.CustomModJsonKeys.PROVIDED_JAVADOC);

			if (customElement == null) {
				return null;
			}

			final String javaDocPath = customElement.getAsString();
			final MemoryMappingTree mappings = new MemoryMappingTree();
			final String mappingsHash;

			try {
				final byte[] data = fabricModJson.getSource().read(javaDocPath);
				mappingsHash = Checksum.of(data).sha1().hex();

				try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data))) {
					// Replace the default fallback namespaces with intermediary and named
					// if the format doesn't have them (this includes the Enigma format, which we want to
					// support since it's produced by ModEnigmaTask).
					final Map<String, String> fallbackNamespaceReplacements = Map.of(
							MappingUtil.NS_SOURCE_FALLBACK, productionNamespace.toString(),
							MappingUtil.NS_TARGET_FALLBACK, MappingsNamespace.NAMED.toString()
					);
					final MappingNsRenamer renamer = new MappingNsRenamer(mappings, fallbackNamespaceReplacements);
					final DstNameCheckingVisitor checker = new DstNameCheckingVisitor(modId, renamer);
					MappingReader.read(reader, checker);
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read javadoc from mod: " + modId, e);
			}

			if (!mappings.getSrcNamespace().equals(productionNamespace.toString())) {
				throw new IllegalStateException("Javadoc provided by mod (%s) must have an %s source namespace".formatted(modId, productionNamespace.toString()));
			}

			return new ModJavadoc(modId, mappings, mappingsHash);
		}

		public void apply(MemoryMappingTree target, boolean disableObfuscation) {
			int targetNamespaceId = target.getNamespaceId(mappingTree.getSrcNamespace());

			if (targetNamespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
				throw new IllegalStateException("Mapping tree must have namespace %s".formatted(mappingTree.getSrcNamespace()));
			}

			for (MappingTree.ClassMapping sourceClass : mappingTree.getClasses()) {
				final MappingTree.ClassMapping targetClass = MappingProcessing.getOrCreateClassMapping(target, sourceClass.getSrcName(), targetNamespaceId, disableObfuscation);

				if (targetClass == null) {
					LOGGER.warn("Could not find provided javadoc target class {} from mod {}", sourceClass.getSrcName(), modId);
					continue;
				}

				applyComment(sourceClass, targetClass);

				for (MappingTree.FieldMapping sourceField : sourceClass.getFields()) {
					final MappingTree.FieldMapping targetField = MappingProcessing.getOrCreateFieldMapping(target, targetClass, sourceField.getSrcName(), sourceField.getSrcDesc(), targetNamespaceId, disableObfuscation);

					if (targetField == null) {
						LOGGER.warn("Could not find provided javadoc target field {}{} from mod {}", sourceField.getSrcName(), sourceField.getSrcDesc(), modId);
						continue;
					}

					applyComment(sourceField, targetField);
				}

				for (MappingTree.MethodMapping sourceMethod : sourceClass.getMethods()) {
					final MappingTree.MethodMapping targetMethod = MappingProcessing.getOrCreateMethodMapping(target, targetClass, sourceMethod.getSrcName(), sourceMethod.getSrcDesc(), targetNamespaceId, disableObfuscation);

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

		// Must override as not to include MemoryMappingTree
		@Override
		public int hashCode() {
			return Objects.hash(modId, mappingsHash);
		}

		@Override
		public String toString() {
			return "ModJavadoc{modId='%s', mappingsHash='%s'}".formatted(modId, mappingsHash);
		}
	}

	private static final class DstNameCheckingVisitor extends ForwardingMappingVisitor {
		private final String modId;

		DstNameCheckingVisitor(String modId, MappingVisitor next) {
			super(next);
			this.modId = modId;
		}

		@Override
		public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
			throw new IllegalStateException("Javadoc provided by mod (%s) must not contain any dst names".formatted(modId));
		}
	}
}
