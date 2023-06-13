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
import java.util.function.BiFunction;
import java.util.function.Function;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.decompile.DecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SingleJarDecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SplitDecompileConfiguration;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.ProcessedNamedMinecraftProvider;

public enum MinecraftJarConfiguration {
	MERGED(
		MergedMinecraftProvider::new,
		IntermediaryMinecraftProvider.MergedImpl::new,
		NamedMinecraftProvider.MergedImpl::new,
		ProcessedNamedMinecraftProvider.MergedImpl::new,
		SingleJarDecompileConfiguration::new,
		List.of("client", "server")
	),
	SERVER_ONLY(
		SingleJarMinecraftProvider::server,
		IntermediaryMinecraftProvider.SingleJarImpl::server,
		NamedMinecraftProvider.SingleJarImpl::server,
		ProcessedNamedMinecraftProvider.SingleJarImpl::server,
		SingleJarDecompileConfiguration::new,
		List.of("server")
	),
	CLIENT_ONLY(
		SingleJarMinecraftProvider::client,
		IntermediaryMinecraftProvider.SingleJarImpl::client,
		NamedMinecraftProvider.SingleJarImpl::client,
		ProcessedNamedMinecraftProvider.SingleJarImpl::client,
		SingleJarDecompileConfiguration::new,
		List.of("client")
	),
	SPLIT(
		SplitMinecraftProvider::new,
		IntermediaryMinecraftProvider.SplitImpl::new,
		NamedMinecraftProvider.SplitImpl::new,
		ProcessedNamedMinecraftProvider.SplitImpl::new,
		SplitDecompileConfiguration::new,
		List.of("client", "server")
	);

	private final Function<ConfigContext, MinecraftProvider> minecraftProviderFunction;
	private final BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>> intermediaryMinecraftProviderBiFunction;
	private final BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>> namedMinecraftProviderBiFunction;
	private final BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> processedNamedMinecraftProviderBiFunction;
	private final BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>> decompileConfigurationBiFunction;
	private final List<String> supportedEnvironments;

	@SuppressWarnings("unchecked") // Just a bit of a generic mess :)
	<M extends MinecraftProvider, P extends NamedMinecraftProvider<M>, Q extends MappedMinecraftProvider> MinecraftJarConfiguration(
			Function<ConfigContext, M> minecraftProviderFunction,
			BiFunction<Project, M, IntermediaryMinecraftProvider<M>> intermediaryMinecraftProviderBiFunction,
			BiFunction<Project, M, P> namedMinecraftProviderBiFunction,
			BiFunction<P, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<M, P>> processedNamedMinecraftProviderBiFunction,
			BiFunction<ConfigContext, Q, DecompileConfiguration<?>> decompileConfigurationBiFunction,
			List<String> supportedEnvironments
	) {
		this.minecraftProviderFunction = (Function<ConfigContext, MinecraftProvider>) minecraftProviderFunction;
		this.intermediaryMinecraftProviderBiFunction = (BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>>) (Object) intermediaryMinecraftProviderBiFunction;
		this.namedMinecraftProviderBiFunction = (BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>>) namedMinecraftProviderBiFunction;
		this.processedNamedMinecraftProviderBiFunction = (BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>>) (Object) processedNamedMinecraftProviderBiFunction;
		this.decompileConfigurationBiFunction = (BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>>) decompileConfigurationBiFunction;
		this.supportedEnvironments = supportedEnvironments;
	}

	public Function<ConfigContext, MinecraftProvider> getMinecraftProviderFunction() {
		return minecraftProviderFunction;
	}

	public BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>> getIntermediaryMinecraftProviderBiFunction() {
		return intermediaryMinecraftProviderBiFunction;
	}

	public BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>> getNamedMinecraftProviderBiFunction() {
		return namedMinecraftProviderBiFunction;
	}

	public BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> getProcessedNamedMinecraftProviderBiFunction() {
		return processedNamedMinecraftProviderBiFunction;
	}

	public BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>> getDecompileConfigurationBiFunction() {
		return decompileConfigurationBiFunction;
	}

	public List<String> getSupportedEnvironments() {
		return supportedEnvironments;
	}
}
