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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.tasks.TaskDependency;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class LayredMappingsDependency implements SelfResolvingDependency {
	private static final String GROUP = "loom";
	private static final String MODULE = "mappings";

	private final MappingContext mappingContext;
	private final LayeredMappingSpec layeredMappingSpec;
	// Version based off the spec hash, if the spec changes we rebuild
	private final int layerHash;

	public LayredMappingsDependency(MappingContext mappingContext, LayeredMappingSpec layeredMappingSpec) {
		this.mappingContext = mappingContext;
		this.layeredMappingSpec = layeredMappingSpec;
		layerHash = this.layeredMappingSpec.hashCode();
	}

	@Override
	public Set<File> resolve() {
		Path mappingsDir = mappingContext.mappingsProvider().getMappingsDir();
		Path mappingsFile = mappingsDir.resolve(String.format("%s.%s-%s.tiny", GROUP, MODULE, getVersion()));

		if (!Files.exists(mappingsFile) || LoomGradlePlugin.refreshDeps) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			try {
				MappingContext context = null; // TODO

				for (MappingsSpec<?> spec : layeredMappingSpec.layers()) {
					MappingLayer layer = spec.createLayer(context);
					layer.visit(mappingTree);
				}

				try (Writer writer = new StringWriter()) {
					Tiny2Writer tiny2Writer = new Tiny2Writer(writer, false);
					mappingTree.accept(tiny2Writer);

					Files.deleteIfExists(mappingsFile);

					ZipUtil.pack(new ZipEntrySource[] {
							new ByteSource("mappings/mappings.tiny", writer.toString().getBytes(StandardCharsets.UTF_8))
					}, mappingsFile.toFile());
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to resolve Mojang mappings", e);
			}
		}

		return Collections.singleton(mappingsFile.toFile());
	}

	@Override
	public Set<File> resolve(boolean transitive) {
		return resolve();
	}

	@Override
	public TaskDependency getBuildDependencies() {
		return task -> Collections.emptySet();
	}

	@Override
	public String getGroup() {
		return GROUP;
	}

	@Override
	public String getName() {
		return MODULE;
	}

	@Override
	public String getVersion() {
		return "layered+hash.%d".formatted(layerHash);
	}

	@Override
	public boolean contentEquals(Dependency dependency) {
		if (dependency instanceof LayredMappingsDependency layredMappingsDependency) {
			return Objects.equals(layredMappingsDependency.getVersion(), this.getVersion());
		}

		return false;
	}

	@Override
	public Dependency copy() {
		return new LayredMappingsDependency(mappingContext, layeredMappingSpec);
	}

	@Override
	public String getReason() {
		return null;
	}

	@Override
	public void because(String s) {
	}
}