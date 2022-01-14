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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.net.UrlEscapers;
import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.format.Tiny2Reader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class IntermediaryService implements SharedService {
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediaryService.class);

	private final Path intermediaryTiny;
	private final Supplier<MemoryMappingTree> memoryMappingTree = Suppliers.memoize(this::createMemoryMappingTree);

	private IntermediaryService(Path intermediaryTiny) {
		this.intermediaryTiny = intermediaryTiny;
	}

	public static synchronized IntermediaryService getInstance(Project project, MinecraftProvider minecraftProvider) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final String encodedMinecraftVersion = UrlEscapers.urlFragmentEscaper().escape(minecraftProvider.minecraftVersion());
		final String intermediaryArtifactUrl = extension.getIntermediaryUrl(encodedMinecraftVersion);

		return SharedServiceManager.get(project).getOrCreateService("IntermediaryService:" + intermediaryArtifactUrl,
				() -> create(intermediaryArtifactUrl, minecraftProvider));
	}

	@VisibleForTesting
	public static IntermediaryService create(String intermediaryUrl, MinecraftProvider minecraftProvider) {
		final Path intermediaryTiny = minecraftProvider.file("intermediary-v2.tiny").toPath();

		if (!Files.exists(intermediaryTiny) || LoomGradlePlugin.refreshDeps) {
			// Download and extract intermediary
			File intermediaryJar = minecraftProvider.file("intermediary-v2.jar");

			try {
				DownloadUtil.downloadIfChanged(new URL(intermediaryUrl), intermediaryJar, LOGGER);
				MappingsProviderImpl.extractMappings(intermediaryJar.toPath(), intermediaryTiny);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to download and extract intermediary", e);
			}
		}

		return new IntermediaryService(intermediaryTiny);
	}

	private MemoryMappingTree createMemoryMappingTree() {
		final MemoryMappingTree tree = new MemoryMappingTree();

		try {
			MappingNsCompleter nsCompleter = new MappingNsCompleter(tree, Collections.singletonMap(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);

			try (BufferedReader reader = Files.newBufferedReader(getIntermediaryTiny(), StandardCharsets.UTF_8)) {
				Tiny2Reader.read(reader, nsCompleter);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read intermediary mappings", e);
		}

		return tree;
	}

	public MemoryMappingTree getMemoryMappingTree() {
		return memoryMappingTree.get();
	}

	public Path getIntermediaryTiny() {
		return Objects.requireNonNull(intermediaryTiny, "Intermediary mappings have not been setup");
	}
}
