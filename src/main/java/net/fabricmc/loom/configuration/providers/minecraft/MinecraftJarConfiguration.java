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

import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.decompile.DecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SingleJarDecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SplitDecompileConfiguration;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.ProcessedNamedMinecraftProvider;

public record MinecraftJarConfiguration<
		M extends MinecraftProvider,
		N extends NamedMinecraftProvider<M>,
		Q extends MappedMinecraftProvider>(
				MinecraftProviderFactory<M> minecraftProviderFactory,
				IntermediaryMinecraftProviderFactory<M> intermediaryMinecraftProviderFactory,
				NamedMinecraftProviderFactory<M> namedMinecraftProviderFactory,
				ProcessedNamedMinecraftProviderFactory<M, N> processedNamedMinecraftProviderFactory,
				DecompileConfigurationFactory<Q> decompileConfigurationFactory,
				List<String> supportedEnvironments) {
	public static final MinecraftJarConfiguration<
			MergedMinecraftProvider,
			NamedMinecraftProvider.MergedImpl,
			MappedMinecraftProvider> MERGED = new MinecraftJarConfiguration<>(
				MergedMinecraftProvider::new,
				IntermediaryMinecraftProvider.MergedImpl::new,
				NamedMinecraftProvider.MergedImpl::new,
				ProcessedNamedMinecraftProvider.MergedImpl::new,
				SingleJarDecompileConfiguration::new,
				List.of("client", "server")
			);
	public static final MinecraftJarConfiguration<
			LegacyMergedMinecraftProvider,
			NamedMinecraftProvider.LegacyMergedImpl,
			MappedMinecraftProvider> LEGACY_MERGED = new MinecraftJarConfiguration<>(
				LegacyMergedMinecraftProvider::new,
				IntermediaryMinecraftProvider.LegacyMergedImpl::new,
				NamedMinecraftProvider.LegacyMergedImpl::new,
				ProcessedNamedMinecraftProvider.LegacyMergedImpl::new,
				SingleJarDecompileConfiguration::new,
				List.of("client", "server")
			);
	public static final MinecraftJarConfiguration<
			SingleJarMinecraftProvider,
			NamedMinecraftProvider.SingleJarImpl,
			MappedMinecraftProvider> SERVER_ONLY = new MinecraftJarConfiguration<>(
				SingleJarMinecraftProvider::server,
				IntermediaryMinecraftProvider.SingleJarImpl::server,
				NamedMinecraftProvider.SingleJarImpl::server,
				ProcessedNamedMinecraftProvider.SingleJarImpl::server,
				SingleJarDecompileConfiguration::new,
				List.of("server")
			);
	public static final MinecraftJarConfiguration<
			SingleJarMinecraftProvider,
			NamedMinecraftProvider.SingleJarImpl,
			MappedMinecraftProvider> CLIENT_ONLY = new MinecraftJarConfiguration<>(
				SingleJarMinecraftProvider::client,
				IntermediaryMinecraftProvider.SingleJarImpl::client,
				NamedMinecraftProvider.SingleJarImpl::client,
				ProcessedNamedMinecraftProvider.SingleJarImpl::client,
				SingleJarDecompileConfiguration::new,
				List.of("client")
			);
	public static final MinecraftJarConfiguration<
			SplitMinecraftProvider,
			NamedMinecraftProvider.SplitImpl,
			MappedMinecraftProvider.Split> SPLIT = new MinecraftJarConfiguration<>(
				SplitMinecraftProvider::new,
				IntermediaryMinecraftProvider.SplitImpl::new,
				NamedMinecraftProvider.SplitImpl::new,
				ProcessedNamedMinecraftProvider.SplitImpl::new,
				SplitDecompileConfiguration::new,
				List.of("client", "server")
			);

	public MinecraftProvider createMinecraftProvider(MinecraftMetadataProvider metadataProvider, ConfigContext context) {
		return minecraftProviderFactory.create(metadataProvider, context);
	}

	public IntermediaryMinecraftProvider<M> createIntermediaryMinecraftProvider(Project project) {
		return intermediaryMinecraftProviderFactory.create(project, getMinecraftProvider(project));
	}

	public NamedMinecraftProvider<M> createNamedMinecraftProvider(Project project) {
		return namedMinecraftProviderFactory.create(project, getMinecraftProvider(project));
	}

	public ProcessedNamedMinecraftProvider<M, N> createProcessedNamedMinecraftProvider(NamedMinecraftProvider<?> namedMinecraftProvider, MinecraftJarProcessorManager jarProcessorManager) {
		return processedNamedMinecraftProviderFactory.create((N) namedMinecraftProvider, jarProcessorManager);
	}

	public DecompileConfiguration<Q> createDecompileConfiguration(Project project) {
		return decompileConfigurationFactory.create(project, getMappedMinecraftProvider(project));
	}

	private M getMinecraftProvider(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		//noinspection unchecked
		return (M) extension.getMinecraftProvider();
	}

	private Q getMappedMinecraftProvider(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		//noinspection unchecked
		return (Q) extension.getNamedMinecraftProvider();
	}

	// Factory interfaces:
	private interface MinecraftProviderFactory<M extends MinecraftProvider> {
		M create(MinecraftMetadataProvider metadataProvider, ConfigContext configContext);
	}

	private interface IntermediaryMinecraftProviderFactory<M extends MinecraftProvider> {
		IntermediaryMinecraftProvider<M> create(Project project, M minecraftProvider);
	}

	private interface NamedMinecraftProviderFactory<M extends MinecraftProvider> {
		NamedMinecraftProvider<M> create(Project project, M minecraftProvider);
	}

	private interface ProcessedNamedMinecraftProviderFactory<M extends MinecraftProvider, N extends NamedMinecraftProvider<M>> {
		ProcessedNamedMinecraftProvider<M, N> create(N namedMinecraftProvider, MinecraftJarProcessorManager jarProcessorManager);
	}

	private interface DecompileConfigurationFactory<M extends MappedMinecraftProvider> {
		DecompileConfiguration<M> create(Project project, M minecraftProvider);
	}
}
