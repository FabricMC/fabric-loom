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

package net.fabricmc.loom.configuration.providers.mappings.mojmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.gradle.api.logging.Logger;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.loom.configuration.providers.mappings.utils.DstNameFilterMappingVisitor;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;

public record MojangMappingLayer(Path clientMappings, Path serverMappings, boolean nameSyntheticMembers,
									Logger logger) implements MappingLayer {
	private static final Pattern SYNTHETIC_NAME_PATTERN = Pattern.compile("^(access|this|val\\$this|lambda\\$.*)\\$[0-9]+$");

	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		printMappingsLicense(clientMappings);

		// Filter out field names matching the pattern
		DstNameFilterMappingVisitor nameFilter = new DstNameFilterMappingVisitor(mappingVisitor, SYNTHETIC_NAME_PATTERN);

		// Make official the source namespace
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(nameSyntheticMembers() ? mappingVisitor : nameFilter, MappingsNamespace.OFFICIAL.toString());

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
		return List.of(IntermediaryMappingLayer.class);
	}
}
