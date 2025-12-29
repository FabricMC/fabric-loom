/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2025 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.mojmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.gradle.api.logging.Logger;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.loom.configuration.providers.mappings.utils.DstNameFilterMappingVisitor;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public record MojangMappingLayer(Path clientMappings, Path serverMappings, boolean nameSyntheticMembers, boolean dropNoneIntermediaryRoots,
									@Nullable Supplier<MemoryMappingTree> intermediarySupplier, Logger logger) implements MappingLayer {
	private static final Pattern SYNTHETIC_NAME_PATTERN = Pattern.compile("^(access|this|val\\$this|lambda\\$.*)\\$[0-9]+$");

	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		printMappingsLicense(clientMappings);

		if (!dropNoneIntermediaryRoots) {
			logger().debug("Not attempting to drop none intermediary roots");

			readMappings(mappingVisitor);
			return;
		}

		logger().info("Attempting to drop none intermediary roots");

		if (intermediarySupplier == null) {
			// Using no-op intermediary mappings
			readMappings(mappingVisitor);
			return;
		}

		// Create a mapping tree with src: official dst: named, intermediary
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		intermediarySupplier.get().accept(mappingTree);
		readMappings(mappingTree);

		// The following code first switches the src namespace to intermediary dropping any entries that don't have an intermediary name
		// This removes any none root methods before switching it back to official
		var officialSwitch = new MappingSourceNsSwitch(mappingVisitor, getSourceNamespace().toString(), false);
		var intermediarySwitch = new MappingSourceNsSwitch(officialSwitch, MappingsNamespace.INTERMEDIARY.toString(), true);
		mappingTree.accept(intermediarySwitch);
	}

	private void readMappings(MappingVisitor mappingVisitor) throws IOException {
		// Filter out field names matching the pattern
		var nameFilter = new DstNameFilterMappingVisitor(mappingVisitor, SYNTHETIC_NAME_PATTERN);

		// Make official the source namespace
		var nsSwitch = new MappingSourceNsSwitch(nameSyntheticMembers() ? mappingVisitor : nameFilter, MappingsNamespace.OFFICIAL.toString());

		// Read both server and client mappings
		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8);
				BufferedReader serverBufferedReader = Files.newBufferedReader(serverMappings, StandardCharsets.UTF_8)) {
			ProGuardFileReader.read(clientBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			ProGuardFileReader.read(serverBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
		}
	}

	private void printMappingsLicense(Path clientMappings) {
		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8)) {
			logger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			logger().warn("Using of the official minecraft mappings is at your own risk!");
			logger().warn("Please make sure to read and understand the following license:");
			logger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			String line;

			while ((line = clientBufferedReader.readLine()).startsWith("#")) {
				logger().warn(line);
			}

			logger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		} catch (IOException e) {
			throw new RuntimeException("Failed to read client mappings", e);
		}
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.OFFICIAL;
	}

	@Override
	public List<Class<? extends MappingLayer>> dependsOn() {
		if (intermediarySupplier == null) {
			return List.of();
		}

		return List.of(IntermediaryMappingLayer.class);
	}
}
