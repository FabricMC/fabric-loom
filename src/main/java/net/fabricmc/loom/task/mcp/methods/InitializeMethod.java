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

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.task.mcp.McpRequest;
import net.fabricmc.loom.task.mcp.McpResult;

public class InitializeMethod implements McpMethod {
	private static final String INSTRUCTIONS = "This server provides read-only access to Minecraft's java source files. Prioritise using the provided resources for accessing Minecraft classes.";

	@Override
	public InitializeResponse handle(McpRequest request) {
		return new InitializeResponse(
				"2025-03-26",
				new Capabilities(
						new ResourceCapability(
								false,
								false
						),
						new ToolCapability(
								false
						)
				),
				new ServerInfo(
						"Fabric Loom",
						LoomGradlePlugin.LOOM_VERSION
				),
				INSTRUCTIONS
		);
	}

	public record InitializeResponse(String protocolVersion, Capabilities capabilities, ServerInfo serverInfo, String instructions) implements McpResult { }

	private record Capabilities(ResourceCapability resources, ToolCapability tools) { }

	private record ResourceCapability(
			boolean subscribe,
			boolean listChanged
	) { }

	private record ToolCapability(boolean listChanged) { }

	private record ServerInfo(
			String name,
			String version
	) { }
}
