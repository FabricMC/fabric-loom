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

package net.fabricmc.loom.providers;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import cuchaz.enigma.command.ConvertMappingsCommand;
import cuchaz.enigma.command.InvertMappingsCommand;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.enigma.EnigmaWriter;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.stitch.commands.CommandReorderTiny;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;
import net.fabricmc.stitch.commands.tinyv2.TinyClass;
import net.fabricmc.stitch.commands.tinyv2.TinyField;
import net.fabricmc.stitch.commands.tinyv2.TinyMethod;

public class McpConfigProvider extends DependencyProvider {
	private File mcp;
	private File srg;
	private File srgTiny;
	private File invertedSrgTiny;

	public McpConfigProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		init(dependency.getDependency().getVersion());

		if (mcp.exists() && srg.exists() && srgTiny.exists() && invertedSrgTiny.exists()) {
			return; // No work for us to do here
		}

		Path mcpZip = dependency.resolveFile().orElseThrow(() -> new IllegalStateException("Could not resolve MCPConfig")).toPath();

		if (!mcp.exists()) {
			Files.copy(mcpZip, mcp.toPath());
		}

		if (!srg.exists()) {
			try (FileSystem fs = FileSystems.newFileSystem(new URI("jar:" + mcpZip.toUri()), ImmutableMap.of("create", false))) {
				Files.copy(fs.getPath("config", "joined.tsrg"), srg.toPath());
			}
		}

		if (!srgTiny.exists()) {
			List<TinyClass> classes = new ArrayList<>();

			try (TSrgReader reader = new TSrgReader(new FileReader(srg))) {
				MappingSet mappings = reader.read();

			}

			new ConvertMappingsCommand().run("enigma_file", enigma.toString(), "tinyv2:official:srg", srgTiny.getAbsolutePath());
			new CommandReorderTinyV2().run(new String[]{srgTiny.getAbsolutePath(), invertedSrgTiny.getAbsolutePath(), "srg", "official"});
		}
	}

	private <M extends ClassMapping<M, ?>> TinyClass toTinyClass(M mapping) {
		List<String> names = ImmutableList.of(mapping.getFullObfuscatedName(), mapping.getFullDeobfuscatedName());
		List<TinyMethod> methods = new ArrayList<>();
		List<TinyField> fields = new ArrayList<>();

		for (MethodMapping method : mapping.getMethodMappings()) {
			List<String> methodNames = ImmutableList.of(method.getObfuscatedName(), method.getDeobfuscatedName());
			methods.add(new TinyMethod(method.getObfuscatedDescriptor(), methodNames, Collections.emptySet(), Collections.emptySet(), Collections.emptySet()));
		}

		for (FieldMapping field : mapping.getFieldMappings()) {
			List<String> fieldNames = ImmutableList.of(field.getObfuscatedName(), field.getDeobfuscatedName());
			fields.add(new TinyField(field.get))
		}

		return new TinyClass(names, methods, fields, Collections.emptySet());
	}

	private void init(String version) {
		mcp = new File(getExtension().getUserCache(), "mcp-" + version + ".zip");
		srg = new File(getExtension().getUserCache(), "srg-" + version + ".tsrg");
		srgTiny = new File(getExtension().getUserCache(), "srg-" + version + ".tiny");
		invertedSrgTiny = new File(getExtension().getUserCache(), "srg-" + version + "-inverted.tiny");
	}

	public File getMcp() {
		return mcp;
	}

	public File getSrg() {
		return srg;
	}

	public File getSrgTiny() {
		return srgTiny;
	}

	public File getInvertedSrgTiny() {
		return invertedSrgTiny;
	}

	@Override
	public String getTargetConfig() {
		return Constants.MCP_CONFIG;
	}
}
