/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public abstract sealed class MinecraftJar permits MinecraftJar.Merged, MinecraftJar.Common, MinecraftJar.ServerOnly, MinecraftJar.ClientOnly {
	private final Path path;
	private final boolean merged, client, server;
	private final String name;

	protected MinecraftJar(Path path, boolean merged, boolean client, boolean server, String name) {
		this.path = Objects.requireNonNull(path);
		this.merged = merged;
		this.client = client;
		this.server = server;
		this.name = name;
	}

	public Path getPath() {
		return path;
	}

	public File toFile() {
		return getPath().toFile();
	}

	public boolean isMerged() {
		return merged;
	}

	public boolean includesClient() {
		return client;
	}

	public boolean includesServer() {
		return server;
	}

	public String getName() {
		return name;
	}

	public abstract MinecraftJar forPath(Path path);

	public static final class Merged extends MinecraftJar {
		public Merged(Path path) {
			super(path, true, true, true, "merged");
		}

		@Override
		public MinecraftJar forPath(Path path) {
			return new Merged(path);
		}
	}

	public static final class Common extends MinecraftJar {
		public Common(Path path) {
			super(path, false, false, true, "common");
		}

		@Override
		public MinecraftJar forPath(Path path) {
			return new Common(path);
		}
	}

	public static final class ServerOnly extends MinecraftJar {
		public ServerOnly(Path path) {
			super(path, false, false, true, "serverOnly");
		}

		@Override
		public MinecraftJar forPath(Path path) {
			return new ServerOnly(path);
		}
	}

	public static final class ClientOnly extends MinecraftJar {
		public ClientOnly(Path path) {
			super(path, false, true, false, "clientOnly");
		}

		@Override
		public MinecraftJar forPath(Path path) {
			return new ClientOnly(path);
		}
	}
}
