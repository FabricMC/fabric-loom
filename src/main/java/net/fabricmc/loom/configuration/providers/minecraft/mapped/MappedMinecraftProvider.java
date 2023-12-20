/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.mapped;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarEnvType;

public interface MappedMinecraftProvider {
	default List<Path> getMinecraftJarPaths() {
		return getMinecraftJars().stream().map(MinecraftJar::getPath).toList();
	}

	List<MinecraftJar> getMinecraftJars();

	interface ProviderImpl extends MappedMinecraftProvider {
		Path getJar(MinecraftJar.Type type);
	}

	interface Merged extends ProviderImpl {
		default MinecraftJar getMergedJar() {
			return new MinecraftJar.Merged(getJar(MinecraftJar.Type.MERGED));
		}

		@Override
		default List<MinecraftJar> getMinecraftJars() {
			return List.of(getMergedJar());
		}
	}

	interface Split extends ProviderImpl {
		default MinecraftJar getCommonJar() {
			return new MinecraftJar.Common(getJar(MinecraftJar.Type.COMMON));
		}

		default MinecraftJar getClientOnlyJar() {
			return new MinecraftJar.ClientOnly(getJar(MinecraftJar.Type.CLIENT_ONLY));
		}

		@Override
		default List<MinecraftJar> getMinecraftJars() {
			return List.of(getCommonJar(), getClientOnlyJar());
		}
	}

	interface SingleJar extends ProviderImpl {
		SingleJarEnvType env();

		default MinecraftJar.Type envType() {
			return env().getType();
		}

		default MinecraftJar getEnvOnlyJar() {
			return env().getJar().apply(getJar(env().getType()));
		}

		@Override
		default List<MinecraftJar> getMinecraftJars() {
			return List.of(getEnvOnlyJar());
		}
	}
}
