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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.loom.task.mcp.McpRequest;
import net.fabricmc.loom.task.mcp.McpResult;
import net.fabricmc.loom.task.mcp.methods.McpMethod;
import net.fabricmc.loom.task.mcp.tools.McpTool;

// https://modelcontextprotocol.io/specification/2025-06-18/server/tools#listing-tools
public class ListToolsMethod implements McpMethod {
	private final Map<String, McpTool> mcpTools;

	public ListToolsMethod(Map<String, McpTool> mcpTools) {
		this.mcpTools = mcpTools;
	}

	@Override
	public ListResponse handle(McpRequest request) {
		List<Tool> tools = new ArrayList<>();

		for (Map.Entry<String, McpTool> entry : mcpTools.entrySet()) {
			McpTool tool = entry.getValue();
			tools.add(new Tool(entry.getKey(), tool.title(), tool.description(), getInputSchema(tool)));
		}

		return new ListResponse(tools);
	}

	private static InputSchema getInputSchema(McpTool tool) {
		return new InputSchema(
				"object",
				tool.getRequiredInputProperties().entrySet()
						.stream()
						.collect(Collectors.toMap(Map.Entry::getKey, e -> new Property("string", e.getValue()))
				),
				new ArrayList<>(tool.getRequiredInputProperties().keySet())
		);
	}

	public record ListResponse(List<Tool> tools) implements McpResult { }

	public record Tool(String name, String title, String description, InputSchema inputSchema) { }

	public record InputSchema(String type, Map<String, Property> properties, List<String> required) { }

	public record Property(String type, String description) { }
}
