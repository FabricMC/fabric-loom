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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loom.task.mcp.McpConstants;
import net.fabricmc.loom.task.mcp.McpException;
import net.fabricmc.loom.task.mcp.McpRequest;
import net.fabricmc.loom.task.mcp.McpResult;
import net.fabricmc.loom.task.mcp.ProjectContext;
import net.fabricmc.loom.task.mcp.methods.McpMethod;
import net.fabricmc.loom.util.ZipUtils;

// https://modelcontextprotocol.io/specification/2025-06-18/server/resources#reading-resources
public class ReadResourceMethod implements McpMethod {
	private final ProjectContext context;

	public ReadResourceMethod(ProjectContext context) {
		this.context = context;
	}

	@Override
	public ContentsResult handle(McpRequest request) throws McpException {
		Object uriObj = request.params().get("uri");

		if (!(uriObj instanceof String uriStr)) {
			throw new McpException(request.id(), McpConstants.ERROR_INVALID_REQUEST, "Missing or invalid 'uri' parameter");
		}

		URI uri = URI.create(uriStr);
		String path = uri.getPath();

		for (Path jar : context.minecraftSourcesJars()) {
			byte[] bytes;

			try {
				bytes = ZipUtils.unpackNullable(jar, path);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read resource from jar: " + jar, e);
			}

			if (bytes == null) {
				// Must be in the next jar
				continue;
			}

			String text = new String(bytes, StandardCharsets.UTF_8);
			return new ContentsResult(List.of(new Content(uri, text, McpConstants.JAVA_MIME_TYPE)));
		}

		throw new McpException(request.id(), McpConstants.ERROR_INVALID_REQUEST, "Resource not found: " + uri);
	}

	public record ContentsResult(List<Content> contents) implements McpResult { }

	public record Content(URI uri, String text, String mimeType) { }
}
