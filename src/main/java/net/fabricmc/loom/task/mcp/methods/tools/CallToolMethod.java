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

package net.fabricmc.loom.task.mcp.methods.tools;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.loom.task.mcp.McpConstants;
import net.fabricmc.loom.task.mcp.McpException;
import net.fabricmc.loom.task.mcp.McpRequest;
import net.fabricmc.loom.task.mcp.McpResult;
import net.fabricmc.loom.task.mcp.Resource;
import net.fabricmc.loom.task.mcp.methods.McpMethod;
import net.fabricmc.loom.task.mcp.tools.McpTool;

// https://modelcontextprotocol.io/specification/2025-06-18/server/tools#calling-tools
public class CallToolMethod implements McpMethod {
	private final Map<String, McpTool> mcpTools;

	public CallToolMethod(Map<String, McpTool> mcpTools) {
		this.mcpTools = mcpTools;
	}

	@Override
	public ToolResult handle(McpRequest request) throws McpException {
		if (request.params() == null || !(request.params().get("name") instanceof String name)) {
			throw new McpException(request.id(), McpConstants.ERROR_INVALID_REQUEST, "Missing or invalid 'name' parameter");
		}

		McpTool tool = mcpTools.get(name);

		if (tool == null) {
			throw new McpException(request.id(), McpConstants.ERROR_INVALID_PARAMS, "Unknown tool: " + name);
		}

		@SuppressWarnings("unchecked")
		Map<String, String> arguments = (Map<String, String>) request.params().get("arguments");

		for (String requiredKey : tool.getRequiredInputProperties().keySet()) {
			if (arguments == null || !arguments.containsKey(requiredKey)) {
				throw new McpException(request.id(), McpConstants.ERROR_INVALID_PARAMS, "Missing required argument: " + requiredKey);
			}
		}

		List<McpTool.Result> results = tool.invoke(arguments);
		List<Content> contents = new ArrayList<>();

		for (McpTool.Result result : results) {
			contents.add(switch (result) {
			case McpTool.ResourceResult resource -> new ResourceContent("resource_link", resource.resource().uri(), resource.resource().name(), resource.resource().title(), resource.resource().description(), resource.resource().description(), resource.resource().annotations());
			case McpTool.TextResult text -> new TextContent("text", text.text());
			});
		}

		return new ToolResult(contents, false);
	}

	public record ToolResult(List<Content> content, boolean isError) implements McpResult { }

	public interface Content { }

	public record TextContent(String type, String text) implements Content { }

	public record ResourceContent(String type, URI uri, String name, String title, String description, String mimeType, Resource.Annotations annotations) implements Content { }
}
