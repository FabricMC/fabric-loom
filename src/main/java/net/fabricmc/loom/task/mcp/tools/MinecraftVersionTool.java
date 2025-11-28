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

package net.fabricmc.loom.task.mcp.tools;

import java.util.List;
import java.util.Map;

import net.fabricmc.loom.task.mcp.ProjectContext;

public class MinecraftVersionTool implements McpTool {
	private final ProjectContext context;

	public MinecraftVersionTool(ProjectContext context) {
		this.context = context;
	}

	@Override
	public String title() {
		return "Minecraft Version Provider";
	}

	@Override
	public String description() {
		return "Gets the current Minecraft version for the current project";
	}

	@Override
	public List<Result> invoke(Map<String, String> arguments) {
		return List.of(
				new TextResult("The current Minecraft version is \"%s\"".formatted(context.minecraftVersion()))
		);
	}
}
