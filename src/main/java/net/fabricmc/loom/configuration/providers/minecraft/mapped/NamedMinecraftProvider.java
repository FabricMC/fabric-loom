/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarEnvType;
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.SplitMinecraftProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class NamedMinecraftProvider<M extends MinecraftProvider> extends AbstractMappedMinecraftProvider<M> {
	public NamedMinecraftProvider(Project project, M minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	public final MappingsNamespace getTargetNamespace() {
		return MappingsNamespace.NAMED;
	}

	@Override
	public MavenScope getMavenScope() {
		return MavenScope.GLOBAL;
	}

	public static final class MergedImpl extends NamedMinecraftProvider<MergedMinecraftProvider> implements Merged {
		public MergedImpl(Project project, MergedMinecraftProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return minecraftProvider.canMergeJars()
				? List.of(
					new RemappedJars(minecraftProvider.getMergedJar(), getMergedJar(), MappingsNamespace.OFFICIAL)
				) : List.of(
					new RemappedJars(minecraftProvider.getClientJar(), getClientJar(), MappingsNamespace.CLIENT_OFFICIAL),
					new RemappedJars(minecraftProvider.getServerJar(), getServerJar(), MappingsNamespace.SERVER_OFFICIAL)
				);
		}

		@Override
		public List<MinecraftJar> provide(ProvideContext context) throws Exception {
			List<MinecraftJar> jars = super.provide(context);

			if (minecraftProvider.canMergeJars()) {
				return jars;
			}

			minecraftProvider.mergeJars(getClientJar().toFile(), getServerJar().toFile(), getMergedJar().toFile());

			return List.of(getMergedJar());
		}

		@Override
		public List<MinecraftJar.Type> getDependencyTypes() {
			return List.of(MinecraftJar.Type.MERGED);
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
			configureSplitRemapper(remappedJars, tinyRemapperBuilder);
		}

		@Override
		public List<MinecraftJar.Type> getDependencyTypes() {
			return List.of(MinecraftJar.Type.CLIENT_ONLY, MinecraftJar.Type.COMMON);
		}
	}

	public static final class SingleJarImpl extends NamedMinecraftProvider<SingleJarMinecraftProvider> implements SingleJar {
		private final SingleJarEnvType env;

		private SingleJarImpl(Project project, SingleJarMinecraftProvider minecraftProvider, SingleJarEnvType env) {
			super(project, minecraftProvider);
			this.env = env;
		}

		public static SingleJarImpl server(Project project, SingleJarMinecraftProvider minecraftProvider) {
			return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.SERVER);
		}

		public static SingleJarImpl client(Project project, SingleJarMinecraftProvider minecraftProvider) {
			return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.CLIENT);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			MappingsNamespace sourceNamespace = MappingsNamespace.OFFICIAL;

			if (!minecraftProvider.canMergeJars()) {
				sourceNamespace = switch (env()) {
				case CLIENT -> MappingsNamespace.CLIENT_OFFICIAL;
				case SERVER -> MappingsNamespace.SERVER_OFFICIAL;
				};
			}

			return List.of(
				new RemappedJars(minecraftProvider.getMinecraftEnvOnlyJar(), getEnvOnlyJar(), sourceNamespace)
			);
		}

		@Override
		public List<MinecraftJar.Type> getDependencyTypes() {
			return List.of(envType());
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}
}
