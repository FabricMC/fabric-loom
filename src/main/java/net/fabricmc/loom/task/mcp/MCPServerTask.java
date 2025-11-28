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

package net.fabricmc.loom.task.mcp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;

import com.sun.net.httpserver.HttpServer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.LoomGradleExtension;

public abstract class MCPServerTask extends DefaultTask {
	@Input
	public abstract Property<Integer> getPort();

	@InputFiles
	public abstract ConfigurableFileCollection getSourceJars();

	@Input
	protected abstract Property<String> getMinecraftVersion();

	@Inject
	public MCPServerTask() {
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		getPort().convention(8080);
		setGroup("fabric");
		setDescription("Starts the fabric-loom MCP server");

		getMinecraftVersion().set(extension.getMinecraftVersion());
		getMinecraftVersion().finalizeValue();
	}

	@TaskAction
	public void startServer() throws IOException {
		final int port = getPort().get();
		getLogger().lifecycle("Starting HTTP server on port {}...", port);
		HttpServer server = createAndStartServer(port, new TaskProjectContext());

		getLogger().lifecycle("MCP server started successfully on http://localhost:{}", port);
		getLogger().lifecycle("Press Ctrl+C to stop");

		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			server.stop(0);
		}
	}

	private final class TaskProjectContext implements ProjectContext {
		@Override
		public String minecraftVersion() {
			return getMinecraftVersion().get();
		}

		@Override
		public List<Path> minecraftSourcesJars() {
			return getSourceJars().getFiles().stream().map(File::toPath).toList();
		}
	}

	@VisibleForTesting
	public static HttpServer createAndStartServer(int port, ProjectContext context) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		server.createContext("/", new McpHttpHandler(context));
		server.setExecutor(null);
		server.start();
		return server;
	}
}
