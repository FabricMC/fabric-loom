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

package net.fabricmc.loom.configuration.providers.minecraft.mapped.intermediary;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.SplitMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SplitMappedMinecraftProvider;
import net.fabricmc.loom.util.SidedClassVisitor;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class SplitIntermediaryMinecraftProvider extends IntermediaryMinecraftProvider<SplitMinecraftProvider> implements SplitMappedMinecraftProvider {
	private Path commonJar;
	private Path clientOnlyJar;

	public SplitIntermediaryMinecraftProvider(Project project, SplitMinecraftProvider minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	public void setupFiles(Function<String, Path> pathFunction) {
		commonJar = pathFunction.apply("common");
		clientOnlyJar = pathFunction.apply("client");
	}

	@Override
	protected List<RemappedJars> getRemappedJars() {
		return List.of(
			new RemappedJars(minecraftProvider.getMinecraftCommonJar().toPath(), commonJar, MappingsNamespace.OFFICIAL),
			new RemappedJars(minecraftProvider.getMinecraftClientOnlyJar().toPath(), clientOnlyJar, MappingsNamespace.OFFICIAL, minecraftProvider.getMinecraftCommonJar().toPath())
		);
	}

	@Override
	protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
		if (remappedJars.outputJar().equals(clientOnlyJar)) {
			tinyRemapperBuilder.extraPostApplyVisitor(SidedClassVisitor.CLIENT);
		}
	}

	@Override
	public Path getCommonJar() {
		return commonJar;
	}

	@Override
	public Path getClientOnlyJar() {
		return clientOnlyJar;
	}
}
