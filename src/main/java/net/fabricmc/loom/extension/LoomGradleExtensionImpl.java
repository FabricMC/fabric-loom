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

package net.fabricmc.loom.extension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadBuilder;
import net.fabricmc.loom.util.gradle.GradleUtils;

public class LoomGradleExtensionImpl extends LoomGradleExtensionApiImpl implements LoomGradleExtension {
	private final Project project;
	private final MixinExtension mixinApExtension;
	private final LoomFiles loomFiles;
	private final ConfigurableFileCollection unmappedMods;

	private final List<AccessWidenerFile> transitiveAccessWideners = new ArrayList<>();

	private LoomDependencyManager dependencyManager;
	private MinecraftProvider minecraftProvider;
	private MappingConfiguration mappingConfiguration;
	private NamedMinecraftProvider<?> namedMinecraftProvider;
	private IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider;
	private InstallerData installerData;
	private boolean refreshDeps;
	private Provider<Boolean> multiProjectOptimisation;
	private final ListProperty<LibraryProcessorManager.LibraryProcessorFactory> libraryProcessorFactories;

	public LoomGradleExtensionImpl(Project project, LoomFiles files) {
		super(project, files);
		this.project = project;
		// Initiate with newInstance to allow gradle to decorate our extension
		this.mixinApExtension = project.getObjects().newInstance(MixinExtensionImpl.class, project);
		this.loomFiles = files;
		this.unmappedMods = project.files();

		// Setup the default intermediate mappings provider.
		setIntermediateMappingsProvider(IntermediaryMappingsProvider.class, provider -> {
			provider.getIntermediaryUrl()
					.convention(getIntermediaryUrl())
					.finalizeValueOnRead();

			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		refreshDeps = manualRefreshDeps();
		multiProjectOptimisation = GradleUtils.getBooleanPropertyProvider(project, Constants.Properties.MULTI_PROJECT_OPTIMISATION);
		libraryProcessorFactories = project.getObjects().listProperty(LibraryProcessorManager.LibraryProcessorFactory.class);
		libraryProcessorFactories.addAll(LibraryProcessorManager.DEFAULT_LIBRARY_PROCESSORS);
		libraryProcessorFactories.finalizeValueOnRead();

		if (refreshDeps) {
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}
	}

	@Override
	protected Project getProject() {
		return project;
	}

	@Override
	public LoomFiles getFiles() {
		return loomFiles;
	}

	@Override
	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}

	@Override
	public LoomDependencyManager getDependencyManager() {
		return Objects.requireNonNull(dependencyManager, "Cannot get LoomDependencyManager before it has been setup");
	}

	@Override
	public MinecraftProvider getMinecraftProvider() {
		return Objects.requireNonNull(minecraftProvider, "Cannot get MinecraftProvider before it has been setup");
	}

	@Override
	public void setMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return Objects.requireNonNull(mappingConfiguration, "Cannot get MappingsProvider before it has been setup");
	}

	@Override
	public void setMappingConfiguration(MappingConfiguration mappingConfiguration) {
		this.mappingConfiguration = mappingConfiguration;
	}

	@Override
	public NamedMinecraftProvider<?> getNamedMinecraftProvider() {
		return Objects.requireNonNull(namedMinecraftProvider, "Cannot get NamedMinecraftProvider before it has been setup");
	}

	@Override
	public IntermediaryMinecraftProvider<?> getIntermediaryMinecraftProvider() {
		return Objects.requireNonNull(intermediaryMinecraftProvider, "Cannot get IntermediaryMinecraftProvider before it has been setup");
	}

	@Override
	public void setNamedMinecraftProvider(NamedMinecraftProvider<?> namedMinecraftProvider) {
		this.namedMinecraftProvider = namedMinecraftProvider;
	}

	@Override
	public void setIntermediaryMinecraftProvider(IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider) {
		this.intermediaryMinecraftProvider = intermediaryMinecraftProvider;
	}

	@Override
	public FileCollection getMinecraftJarsCollection(MappingsNamespace mappingsNamespace) {
		return getProject().files(
			getProject().provider(() ->
				getProject().files(getMinecraftJars(mappingsNamespace).stream().map(Path::toFile).toList())
			)
		);
	}

	@Override
	public ConfigurableFileCollection getUnmappedModCollection() {
		return unmappedMods;
	}

	public void setInstallerData(InstallerData object) {
		this.installerData = object;
	}

	@Override
	public InstallerData getInstallerData() {
		return installerData;
	}

	@Override
	public boolean isRootProject() {
		return project.getRootProject() == project;
	}

	@Override
	public MixinExtension getMixin() {
		return this.mixinApExtension;
	}

	@Override
	public List<AccessWidenerFile> getTransitiveAccessWideners() {
		return transitiveAccessWideners;
	}

	@Override
	public void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles) {
		transitiveAccessWideners.addAll(accessWidenerFiles);
	}

	@Override
	public DownloadBuilder download(String url) {
		DownloadBuilder builder;

		try {
			builder = Download.create(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to create downloader for: " + e);
		}

		if (project.getGradle().getStartParameter().isOffline()) {
			builder.offline();
		}

		if (manualRefreshDeps()) {
			builder.forceDownload();
		}

		return builder;
	}

	private boolean manualRefreshDeps() {
		return project.getGradle().getStartParameter().isRefreshDependencies() || Boolean.getBoolean("loom.refresh");
	}

	@Override
	public boolean refreshDeps() {
		return refreshDeps;
	}

	@Override
	public void setRefreshDeps(boolean refreshDeps) {
		this.refreshDeps = refreshDeps;
	}

	@Override
	public boolean multiProjectOptimisation() {
		return multiProjectOptimisation.getOrElse(false);
	}

	@Override
	public ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors() {
		return libraryProcessorFactories;
	}

	@Override
	protected <T extends IntermediateMappingsProvider> void configureIntermediateMappingsProviderInternal(T provider) {
		provider.getMinecraftVersion().set(getProject().provider(() -> getMinecraftProvider().minecraftVersion()));
		provider.getMinecraftVersion().disallowChanges();

		provider.getDownloader().set(this::download);
		provider.getDownloader().disallowChanges();
	}
}
