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

package net.fabricmc.loom.configuration.providers.mappings.mojmap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.tasks.TaskDependency;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.mappings.parchment.GradleParchmentFileResolver;
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentDataReader;
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentFileResolver;
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentTreeV1;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class MojangMappingsDependency implements SelfResolvingDependency {
	public static final String GROUP = "net.minecraft";
	public static final String MODULE = "mappings";
	// Keys in dependency manifest
	private static final String MANIFEST_CLIENT_MAPPINGS = "client_mappings";
	private static final String MANIFEST_SERVER_MAPPINGS = "server_mappings";

	private final Project project;
	private final LoomGradleExtension extension;
	private final MojangMappingsSpec mojangMappingsSpec;

	private boolean shownLicenseWarning = false;

	public MojangMappingsDependency(Project project, LoomGradleExtension extension, MojangMappingsSpec mojangMappingsSpec) {
		this.project = project;
		this.extension = extension;
		this.mojangMappingsSpec = mojangMappingsSpec;
	}

	@Override
	public Set<File> resolve() {
		Path mappingsDir = extension.getMappingsProvider().getMappingsDir();
		// TODO include parchment name here
		Path mappingsFile = mappingsDir.resolve(String.format("%s.%s-%s.tiny", GROUP, MODULE, getVersion()));
		Path clientMappings = mappingsDir.resolve(String.format("%s.%s-%s-client.map", GROUP, MODULE, getVersion()));
		Path serverMappings = mappingsDir.resolve(String.format("%s.%s-%s-server.map", GROUP, MODULE, getVersion()));

		if (!Files.exists(mappingsFile) || LoomGradlePlugin.refreshDeps) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			try {
				MinecraftVersionMeta versionInfo = extension.getMinecraftProvider().getVersionInfo();

				if (versionInfo.download(MANIFEST_CLIENT_MAPPINGS) == null) {
					throw new RuntimeException("Failed to find official mojang mappings for " + getVersion());
				}

				MojangMappingsResolver mojangMappingsResolver = new MojangMappingsResolver(
						versionInfo.download(MANIFEST_CLIENT_MAPPINGS),
						versionInfo.download(MANIFEST_SERVER_MAPPINGS),
						clientMappings.toFile(),
						serverMappings.toFile(),
						project.getLogger()
				);

				mojangMappingsResolver.visit(extension.getMappingsProvider().getIntermediaryTiny(), mappingTree);

				if (mojangMappingsSpec.isUsingParchment()) {
					ParchmentFileResolver parchmentFileResolver = new GradleParchmentFileResolver(project, mojangMappingsSpec);
					ParchmentDataReader parchmentDataReader = new ParchmentDataReader(parchmentFileResolver);
					ParchmentTreeV1 parchmentData = parchmentDataReader.getParchmentData();

					parchmentData.visit(mappingTree, MojangMappingsResolver.PG_TARGET_NS);
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

		if (!shownLicenseWarning) {
			printMappingsLicense(clientMappings);
		}

		return Collections.singleton(mappingsFile.toFile());
	}

	private void printMappingsLicense(Path clientMappings) {
		shownLicenseWarning = true;

		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8)) {
			project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			project.getLogger().warn("Using of the official minecraft mappings is at your own risk!");
			project.getLogger().warn("Please make sure to read and understand the following license:");
			project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			String line;

			while ((line = clientBufferedReader.readLine()).startsWith("#")) {
				project.getLogger().warn(line);
			}

			project.getLogger().warn("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		} catch (IOException e) {
			throw new RuntimeException("Failed to read client mappings", e);
		}
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
		return extension.getMinecraftProvider().getMinecraftVersion();
	}

	@Override
	public boolean contentEquals(Dependency dependency) {
		if (dependency instanceof MojangMappingsDependency mojangMappingsDependency) {
			return mojangMappingsDependency.extension.getMinecraftProvider().getMinecraftVersion().equals(getVersion());
		}

		return false;
	}

	@Override
	public Dependency copy() {
		return new MojangMappingsDependency(project, extension, mojangMappingsSpec);
	}

	@Override
	public String getReason() {
		return null;
	}

	@Override
	public void because(String s) {
	}
}
