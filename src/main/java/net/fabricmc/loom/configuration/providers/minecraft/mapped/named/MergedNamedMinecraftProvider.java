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

package net.fabricmc.loom.configuration.providers.minecraft.mapped.named;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MergedMappedMinecraftProvider;
import net.fabricmc.loom.util.Constants;

public final class MergedNamedMinecraftProvider extends NamedMinecraftProvider<MergedMinecraftProvider> implements MergedMappedMinecraftProvider {
	private Path mergedJar;

	public MergedNamedMinecraftProvider(Project project, MergedMinecraftProvider minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	public void setupFiles(Function<String, Path> pathFunction) {
		mergedJar = pathFunction.apply("merged");
	}

	@Override
	public List<RemappedJars> getRemappedJars() {
		return List.of(
				new RemappedJars(minecraftProvider.getMergedJar().toPath(), mergedJar, MappingsNamespace.OFFICIAL)
		);
	}

	@Override
	protected void applyDependencies() {
		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED, getDependencyNotation("merged"));
	}

	@Override
	public Path getMergedJar() {
		return mergedJar;
	}
}
