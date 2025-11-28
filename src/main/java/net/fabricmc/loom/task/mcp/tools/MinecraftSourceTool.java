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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.task.mcp.ProjectContext;
import net.fabricmc.loom.task.mcp.methods.resources.ListResourcesMethod;
import net.fabricmc.loom.util.FileSystemUtil;

public class MinecraftSourceTool implements McpTool {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftSourceTool.class);

	private final ProjectContext context;

	public MinecraftSourceTool(ProjectContext context) {
		this.context = context;
	}

	@Override
	public String title() {
		return "Minecraft source code provider";
	}

	@Override
	public String description() {
		return "A tool that provides the decompiled Java source code for a given Minecraft class.";
	}

	@Override
	public Map<String, String> getRequiredInputProperties() {
		return Map.of("name", "A minecraft class name, in one of the following formats: 'ClassName', 'net/minecraft/package/ClassName'");
	}

	@Override
	public List<Result> invoke(Map<String, String> arguments) {
		String name = Objects.requireNonNull(arguments.get("name"));

		List<Result> results = new ArrayList<>();

		for (Path jar : context.minecraftSourcesJars()) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar, false);
					Stream<Path> walk = Files.walk(fs.getRoot())) {
				Iterator<Path> iterator = walk.iterator();

				while (iterator.hasNext()) {
					Path fsPath = iterator.next();

					if (!Files.isRegularFile(fsPath)) {
						continue;
					}

					if (!matchesName(fsPath.toString(), name)) {
						continue;
					}

					String file = fsPath.toString().replace('\\', '/');
					results.add(new ResourceResult(ListResourcesMethod.classFilenameToResource(file)));
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read sources jar: " + jar, e);
			}
		}

		LOGGER.info("Found {} source files matching name '{}'", results.size(), name);

		if (results.isEmpty()) {
			LOGGER.warn("No source files found matching name '{}'", name);
		}

		return results;
	}

	private static boolean matchesName(String path, String name) {
		String normalizedPath = path.replace('\\', '/');
		String className = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1, normalizedPath.length() - ".java".length());
		String fullName = normalizedPath.substring(1, normalizedPath.length() - ".java".length()).replace('/', '.');

		return className.equals(name) || fullName.equals(name) || fullName.endsWith("." + name);
	}
}
