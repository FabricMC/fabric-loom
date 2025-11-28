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

package net.fabricmc.loom.task.mcp.methods;

import java.util.Map;

import net.fabricmc.loom.task.mcp.ProjectContext;
import net.fabricmc.loom.task.mcp.methods.notifications.CancelledMethod;
import net.fabricmc.loom.task.mcp.methods.notifications.InitializedMethod;
import net.fabricmc.loom.task.mcp.methods.prompts.ListPromptsMethod;
import net.fabricmc.loom.task.mcp.methods.resources.ListResourceTemplatesMethod;
import net.fabricmc.loom.task.mcp.methods.resources.ListResourcesMethod;
import net.fabricmc.loom.task.mcp.methods.resources.ReadResourceMethod;
import net.fabricmc.loom.task.mcp.methods.tools.CallToolMethod;
import net.fabricmc.loom.task.mcp.methods.tools.ListToolsMethod;
import net.fabricmc.loom.task.mcp.tools.McpTool;
import net.fabricmc.loom.task.mcp.tools.McpTools;

public final class McpMethods {
	private McpMethods() {
	}

	public static Map<String, McpMethod> getMethods(ProjectContext context) {
		Map<String, McpTool> tools = McpTools.getTools(context);

		return Map.of(
				"initialize", new InitializeMethod(),
				"notifications/initialized", new InitializedMethod(),
				"notifications/cancelled", new CancelledMethod(),
				"resources/list", new ListResourcesMethod(context),
				"resources/read", new ReadResourceMethod(context),
				"resources/templates/list", new ListResourceTemplatesMethod(),
				"tools/list", new ListToolsMethod(tools),
				"tools/call", new CallToolMethod(tools),
				"prompts/list", new ListPromptsMethod()
		);
	}
}
