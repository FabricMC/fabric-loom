/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskDependency;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.extras.unpick.UnpickLayer;
import net.fabricmc.loom.configuration.providers.mappings.utils.AddConstructorMappingVisitor;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class LayeredMappingsDependency implements SelfResolvingDependency, FileCollectionDependency {
	private static final String GROUP = "loom";
	private static final String MODULE = "mappings";

	private final Project project;
	private final MappingContext mappingContext;
	private final LayeredMappingSpec layeredMappingSpec;
	private final String version;

	public LayeredMappingsDependency(Project project, MappingContext mappingContext, LayeredMappingSpec layeredMappingSpec, String version) {
		this.project = project;
		this.mappingContext = mappingContext;
		this.layeredMappingSpec = layeredMappingSpec;
		this.version = version;
	}

	@Override
	public Set<File> resolve() {
		Path mappingsDir = mappingContext.minecraftProvider().dir("layered").toPath();
		Path mappingsFile = mappingsDir.resolve(String.format("%s.%s-%s.tiny", GROUP, MODULE, getVersion()));

		if (!Files.exists(mappingsFile) || mappingContext.refreshDeps()) {
			try {
				var processor = new LayeredMappingsProcessor(layeredMappingSpec);
				List<MappingLayer> layers = processor.resolveLayers(mappingContext);

				Files.deleteIfExists(mappingsFile);

				writeMapping(processor, layers, mappingsFile);
				writeSignatureFixes(processor, layers, mappingsFile);
				writeUnpickData(processor, layers, mappingsFile);
			} catch (IOException e) {
				throw new RuntimeException("Failed to resolve layered mappings", e);
			}
		}

		return Collections.singleton(mappingsFile.toFile());
	}

	private void writeMapping(LayeredMappingsProcessor processor, List<MappingLayer> layers, Path mappingsFile) throws IOException {
		MemoryMappingTree mappings = processor.getMappings(layers);

		try (Writer writer = new StringWriter()) {
			var tiny2Writer = new Tiny2FileWriter(writer, false);

			MappingDstNsReorder nsReorder = new MappingDstNsReorder(tiny2Writer, Collections.singletonList(MappingsNamespace.NAMED.toString()));
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(nsReorder, MappingsNamespace.INTERMEDIARY.toString(), true);
			AddConstructorMappingVisitor addConstructor = new AddConstructorMappingVisitor(nsSwitch);
			mappings.accept(addConstructor);

			Files.deleteIfExists(mappingsFile);
			ZipUtils.add(mappingsFile, "mappings/mappings.tiny", writer.toString().getBytes(StandardCharsets.UTF_8));
		}
	}

	private void writeSignatureFixes(LayeredMappingsProcessor processor, List<MappingLayer> layers, Path mappingsFile) throws IOException {
		Map<String, String> signatureFixes = processor.getSignatureFixes(layers);

		if (signatureFixes == null) {
			return;
		}

		byte[] data = LoomGradlePlugin.GSON.toJson(signatureFixes).getBytes(StandardCharsets.UTF_8);

		ZipUtils.add(mappingsFile, "extras/record_signatures.json", data);
	}

	private void writeUnpickData(LayeredMappingsProcessor processor, List<MappingLayer> layers, Path mappingsFile) throws IOException {
		UnpickLayer.UnpickData unpickData = processor.getUnpickData(layers);

		if (unpickData == null) {
			return;
		}

		ZipUtils.add(mappingsFile, "extras/definitions.unpick", unpickData.definitions());
		ZipUtils.add(mappingsFile, "extras/unpick.json", unpickData.metadata().asJson());
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
		return version;
	}

	@Override
	public boolean contentEquals(Dependency dependency) {
		if (dependency instanceof LayeredMappingsDependency layeredMappingsDependency) {
			return Objects.equals(layeredMappingsDependency.getVersion(), this.getVersion());
		}

		return false;
	}

	@Override
	public Dependency copy() {
		return new LayeredMappingsDependency(project, mappingContext, layeredMappingSpec, version);
	}

	@Override
	public String getReason() {
		return null;
	}

	@Override
	public void because(String s) {
	}

	@Override
	public FileCollection getFiles() {
		return project.files(resolve());
	}
}
