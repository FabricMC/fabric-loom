/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2023 FabricMC
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Provider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.library.MinecraftLibraryHelper;
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.RuntimeLog4jLibraryProcessor;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;

public class MinecraftLibraryProvider {
	private static final Platform platform = Platform.CURRENT;

	private final Project project;
	private final MinecraftProvider minecraftProvider;
	private final LibraryProcessorManager processorManager;

	public MinecraftLibraryProvider(MinecraftProvider minecraftProvider, Project project) {
		this.project = project;
		this.minecraftProvider = minecraftProvider;
		this.processorManager = new LibraryProcessorManager(platform, project.getRepositories(), getEnabledProcessors());
	}

	private List<String> getEnabledProcessors() {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);

		var enabledProcessors = new ArrayList<String>();

		if (extension.getRuntimeOnlyLog4j().get()) {
			enabledProcessors.add(RuntimeLog4jLibraryProcessor.class.getSimpleName());
		}

		final Provider<String> libraryProcessorsProperty = project.getProviders().gradleProperty(Constants.Properties.LIBRARY_PROCESSORS);

		if (libraryProcessorsProperty.isPresent()) {
			String[] split = libraryProcessorsProperty.get().split(":");
			enabledProcessors.addAll(Arrays.asList(split));
		}

		return Collections.unmodifiableList(enabledProcessors);
	}

	public void provide() {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();

		final boolean provideClient = jarConfiguration.getSupportedEnvironments().contains("client");
		final boolean provideServer = jarConfiguration.getSupportedEnvironments().contains("server");
		assert provideClient || provideServer;

		if (provideClient) {
			provideClientLibraries();
		}

		if (provideServer) {
			provideServerLibraries();
		}
	}

	private void provideClientLibraries() {
		final List<Library> libraries = MinecraftLibraryHelper.getLibrariesForPlatform(minecraftProvider.getVersionInfo(), platform);
		final List<Library> processLibraries = processLibraries(libraries);
		processLibraries.forEach(this::applyClientLibrary);

		// After Minecraft 1.19-pre1 the natives should be on the runtime classpath.
		if (!minecraftProvider.getVersionInfo().hasNativesToExtract()) {
			project.getConfigurations().named(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES, configuration -> configuration.extendsFrom(project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_NATIVES)));
		}
	}

	private void provideServerLibraries() {
		final BundleMetadata serverBundleMetadata = minecraftProvider.getServerBundleMetadata();

		if (serverBundleMetadata == null) {
			return;
		}

		final List<Library> libraries = MinecraftLibraryHelper.getServerLibraries(serverBundleMetadata);
		final List<Library> processLibraries = processLibraries(libraries);
		processLibraries.forEach(this::applyServerLibrary);
	}

	private List<Library> processLibraries(List<Library> libraries) {
		final LibraryContext libraryContext = new LibraryContext(minecraftProvider.getVersionInfo(), JavaVersion.current());
		return processorManager.processLibraries(libraries, libraryContext);
	}

	private void applyClientLibrary(Library library) {
		switch (library.target()) {
		case COMPILE -> addLibrary(Constants.Configurations.MINECRAFT_CLIENT_COMPILE_LIBRARIES, library);
		case RUNTIME -> addLibrary(Constants.Configurations.MINECRAFT_CLIENT_RUNTIME_LIBRARIES, library);
		case NATIVES -> addLibrary(Constants.Configurations.MINECRAFT_NATIVES, library);
		case LOCAL_MOD -> applyLocalModLibrary(library);
		}
	}

	private void applyServerLibrary(Library library) {
		switch (library.target()) {
		case COMPILE -> addLibrary(Constants.Configurations.MINECRAFT_SERVER_COMPILE_LIBRARIES, library);
		case RUNTIME -> addLibrary(Constants.Configurations.MINECRAFT_SERVER_RUNTIME_LIBRARIES, library);
		case LOCAL_MOD -> applyLocalModLibrary(library);
		default -> throw new IllegalStateException("Target not supported for server library: %s".formatted(library));
		}
	}

	private void applyLocalModLibrary(Library library) {
		ExternalModuleDependency dependency = (ExternalModuleDependency) project.getDependencies().create(library.mavenNotation());
		dependency.setTransitive(false);
		project.getDependencies().add("modLocalRuntime", dependency);
	}

	private void addLibrary(String configuration, Library library) {
		addDependency(configuration, library.mavenNotation());
	}

	private void addDependency(String configuration, Object dependency) {
		final Dependency created = project.getDependencies().add(configuration, dependency);

		// The launcher doesn't download transitive deps, so neither will we.
		// This will also prevent a LaunchWrapper library dependency from pulling in outdated ASM jars.
		if (created instanceof ModuleDependency md) {
			md.setTransitive(false);
		}
	}
}
