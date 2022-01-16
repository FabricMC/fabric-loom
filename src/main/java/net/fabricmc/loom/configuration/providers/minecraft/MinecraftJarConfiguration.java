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
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.decompile.DecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SingleJarDecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SplitDecompileConfiguration;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
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
		SingleJarDecompileConfiguration.Merged::new,
		List.of("client", "server")
	),
	SERVER_ONLY(
		ServerOnlyMinecraftProvider::new,
		IntermediaryMinecraftProvider.ServerOnlyImpl::new,
		NamedMinecraftProvider.ServerOnlyImpl::new,
		ProcessedNamedMinecraftProvider.ServerOnlyImpl::new,
		SingleJarDecompileConfiguration.ServerOnly::new,
		List.of("server")
	),
	SPLIT(
		SplitMinecraftProvider::new,
		IntermediaryMinecraftProvider.SplitImpl::new,
		NamedMinecraftProvider.SplitImpl::new,
		ProcessedNamedMinecraftProvider.SplitImpl::new,
		SplitDecompileConfiguration::new,
		List.of("client", "server")
	);

	public static final String PROPERTY_KEY = "fabric.loom.minecraft.jar.configuration";

	private final Function<Project, MinecraftProvider> minecraftProviderFunction;
	private final BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>> intermediaryMinecraftProviderBiFunction;
	private final BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>> namedMinecraftProviderBiFunction;
	private final BiFunction<NamedMinecraftProvider<?>, JarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> processedNamedMinecraftProviderBiFunction;
	private final BiFunction<Project, MappedMinecraftProvider, DecompileConfiguration<?>> decompileConfigurationBiFunction;
	private final List<String> supportedRunEnvironments;

	@SuppressWarnings("unchecked") // Just a bit of a generic mess :)
	<M extends MinecraftProvider, P extends NamedMinecraftProvider<M>, Q extends MappedMinecraftProvider> MinecraftJarConfiguration(
			Function<Project, M> minecraftProviderFunction,
			BiFunction<Project, M, IntermediaryMinecraftProvider<M>> intermediaryMinecraftProviderBiFunction,
			BiFunction<Project, M, P> namedMinecraftProviderBiFunction,
			BiFunction<P, JarProcessorManager, ProcessedNamedMinecraftProvider<M, P>> processedNamedMinecraftProviderBiFunction,
			BiFunction<Project, Q, DecompileConfiguration<?>> decompileConfigurationBiFunction,
			List<String> supportedRunEnvironments
	) {
		this.minecraftProviderFunction = (Function<Project, MinecraftProvider>) minecraftProviderFunction;
		this.intermediaryMinecraftProviderBiFunction = (BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>>) (Object) intermediaryMinecraftProviderBiFunction;
		this.namedMinecraftProviderBiFunction = (BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>>) namedMinecraftProviderBiFunction;
		this.processedNamedMinecraftProviderBiFunction = (BiFunction<NamedMinecraftProvider<?>, JarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>>) (Object) processedNamedMinecraftProviderBiFunction;
		this.decompileConfigurationBiFunction = (BiFunction<Project, MappedMinecraftProvider, DecompileConfiguration<?>>) decompileConfigurationBiFunction;
		this.supportedRunEnvironments = supportedRunEnvironments;
	}

	public static MinecraftJarConfiguration fromProjectConfiguration(Project project) {
		Object value = project.getProperties().get(PROPERTY_KEY);

		if (value == null) {
			return MERGED;
		}

		if (value instanceof String str) {
			return Objects.requireNonNull(valueOf(str.toUpperCase(Locale.ROOT)));
		}

		throw new IllegalStateException("");
	}

	public Function<Project, MinecraftProvider> getMinecraftProviderFunction() {
		return minecraftProviderFunction;
	}

	public BiFunction<Project, MinecraftProvider, IntermediaryMinecraftProvider<?>> getIntermediaryMinecraftProviderBiFunction() {
		return intermediaryMinecraftProviderBiFunction;
	}

	public BiFunction<Project, MinecraftProvider, NamedMinecraftProvider<?>> getNamedMinecraftProviderBiFunction() {
		return namedMinecraftProviderBiFunction;
	}

	public BiFunction<NamedMinecraftProvider<?>, JarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> getProcessedNamedMinecraftProviderBiFunction() {
		return processedNamedMinecraftProviderBiFunction;
	}

	public BiFunction<Project, MappedMinecraftProvider, DecompileConfiguration<?>> getDecompileConfigurationBiFunction() {
		return decompileConfigurationBiFunction;
	}

	public List<String> getSupportedRunEnvironments() {
		return supportedRunEnvironments;
	}
}
