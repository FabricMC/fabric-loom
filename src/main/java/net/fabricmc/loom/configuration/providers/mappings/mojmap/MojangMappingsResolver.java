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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.logging.Logger;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.format.Tiny2Reader;

public class MojangMappingsResolver {
	public static final String PG_SOURCE_NS = "official";
	public static final String PG_TARGET_NS = "named";

	private final MinecraftVersionMeta.Download clientMappingsDownload;
	private final MinecraftVersionMeta.Download serverMappingsDownload;
	private final File clientMappingsFile;
	private final File serverMappingsFile;
	private final Logger logger;

	public MojangMappingsResolver(MinecraftVersionMeta.Download clientMappingsDownload, MinecraftVersionMeta.Download serverMappingsDownload, File clientMappingsFile, File serverMappingsFile, Logger logger) {
		this.clientMappingsDownload = clientMappingsDownload;
		this.serverMappingsDownload = serverMappingsDownload;
		this.clientMappingsFile = clientMappingsFile;
		this.serverMappingsFile = serverMappingsFile;
		this.logger = logger;
	}

	private void download() throws IOException {
		HashedDownloadUtil.downloadIfInvalid(new URL(clientMappingsDownload.url()), clientMappingsFile, clientMappingsDownload.sha1(), logger, false);
		HashedDownloadUtil.downloadIfInvalid(new URL(serverMappingsDownload.url()), serverMappingsFile, serverMappingsDownload.sha1(), logger, false);
	}

	public void visit(Path intermediaryTiny, MappingVisitor mappingVisitor) throws IOException {
		download();

		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappingsFile.toPath(), StandardCharsets.UTF_8);
				BufferedReader serverBufferedReader = Files.newBufferedReader(serverMappingsFile.toPath(), StandardCharsets.UTF_8)) {
			ProGuardReader.read(clientBufferedReader, PG_SOURCE_NS, PG_TARGET_NS, mappingVisitor);
			ProGuardReader.read(serverBufferedReader, PG_SOURCE_NS, PG_TARGET_NS, mappingVisitor);
		}

		try (BufferedReader reader = Files.newBufferedReader(intermediaryTiny, StandardCharsets.UTF_8)) {
			Tiny2Reader.read(reader, mappingVisitor);
		}
	}
}
