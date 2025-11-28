/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.mcp.methods.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.fabricmc.loom.task.mcp.McpConstants;
import net.fabricmc.loom.task.mcp.McpRequest;
import net.fabricmc.loom.task.mcp.McpResult;
import net.fabricmc.loom.task.mcp.ProjectContext;
import net.fabricmc.loom.task.mcp.Resource;
import net.fabricmc.loom.task.mcp.methods.McpMethod;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.Lazy;

// https://modelcontextprotocol.io/specification/2025-06-18/server/resources#listing-resources
public final class ListResourcesMethod implements McpMethod {
	private final ProjectContext context;

	private final Supplier<ListResponse> cachedResponse = Lazy.of(this::buildResponse);

	public ListResourcesMethod(ProjectContext context) {
		this.context = context;
	}

	@Override
	public ListResponse handle(McpRequest request) {
		return cachedResponse.get();
	}

	private ListResponse buildResponse() {
		List<Resource> resources = new ArrayList<>();

		for (Path jar : context.minecraftSourcesJars()) {
			List<String> sourceFiles;

			try {
				sourceFiles = getSourceFiles(jar);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read sources jar: " + jar, e);
			}

			for (String file : sourceFiles) {
				if (file.contains("package-info")) {
					continue;
				}

				resources.add(classFilenameToResource(file));
			}
		}

		return new ListResponse(resources);
	}

	public static Resource classFilenameToResource(String file) {
		String fullName = file.replace(".java", "").substring(1);
		String className = fullName.substring(fullName.lastIndexOf('/') + 1);

		return new Resource(
				URI.create("file://%s".formatted(file)),
				className,
				"Minecraft java source file: " + fullName,
				"Minecraft class: " + fullName.replace("/", "."),
				McpConstants.JAVA_MIME_TYPE,
				new Resource.Annotations(
						List.of("user", "assistant"),
						0.8F
				)
		);
	}

	private static List<String> getSourceFiles(Path zip) throws IOException {
		List<String> entries = new ArrayList<>();

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false);
				Stream<Path> walk = Files.walk(fs.getRoot())) {
			Iterator<Path> iterator = walk.iterator();

			while (iterator.hasNext()) {
				Path fsPath = iterator.next();

				if (!Files.isRegularFile(fsPath)) {
					continue;
				}

				String entryPath = fsPath.toString().replace('\\', '/');
				entries.add(entryPath);
			}
		}

		return entries;
	}

	public record ListResponse(List<Resource> resources) implements McpResult {
	}
}
