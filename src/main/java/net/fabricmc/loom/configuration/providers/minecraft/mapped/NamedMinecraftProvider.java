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
import java.util.function.BiConsumer;

import org.gradle.api.Project;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.ServerOnlyMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.SplitMinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.SidedClassVisitor;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class NamedMinecraftProvider<M extends MinecraftProvider> extends AbstractMappedMinecraftProvider<M> {
	public NamedMinecraftProvider(Project project, M minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	protected Path getDirectory() {
		return extension.getMappingsProvider().mappingsWorkingDir();
	}

	@Override
	public final MappingsNamespace getTargetNamespace() {
		return MappingsNamespace.NAMED;
	}

	public static final class MergedImpl extends NamedMinecraftProvider<MergedMinecraftProvider> implements Merged {
		public MergedImpl(Project project, MergedMinecraftProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
					new RemappedJars(minecraftProvider.getMergedJar(), getMergedJar(), MappingsNamespace.OFFICIAL)
			);
		}

		@Override
		protected void applyDependencies(BiConsumer<String, String> consumer) {
			consumer.accept(Constants.Configurations.MINECRAFT_NAMED, MERGED);
		}
	}

	public static final class SplitImpl extends NamedMinecraftProvider<SplitMinecraftProvider> implements Split {
		public SplitImpl(Project project, SplitMinecraftProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
					new RemappedJars(minecraftProvider.getMinecraftCommonJar(), getCommonJar(), MappingsNamespace.OFFICIAL),
					new RemappedJars(minecraftProvider.getMinecraftClientOnlyJar(), getClientOnlyJar(), MappingsNamespace.OFFICIAL, minecraftProvider.getMinecraftCommonJar())
			);
		}

		@Override
		protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
			if (remappedJars.outputJar().equals(getClientOnlyJar())) {
				tinyRemapperBuilder.extraPostApplyVisitor(SidedClassVisitor.CLIENT);
			}
		}

		@Override
		protected void applyDependencies(BiConsumer<String, String> consumer) {
			consumer.accept(Constants.Configurations.MINECRAFT_NAMED, COMMON);
			consumer.accept(Constants.Configurations.MINECRAFT_NAMED, CLIENT_ONLY);
		}
	}

	public static final class ServerOnlyImpl extends NamedMinecraftProvider<ServerOnlyMinecraftProvider> implements ServerOnly {
		public ServerOnlyImpl(Project project, ServerOnlyMinecraftProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
				new RemappedJars(minecraftProvider.getMinecraftServerOnlyJar(), getServerOnlyJar(), MappingsNamespace.OFFICIAL)
			);
		}

		@Override
		protected void applyDependencies(BiConsumer<String, String> consumer) {
			consumer.accept(Constants.Configurations.MINECRAFT_NAMED, SERVER_ONLY);
		}
	}
}
