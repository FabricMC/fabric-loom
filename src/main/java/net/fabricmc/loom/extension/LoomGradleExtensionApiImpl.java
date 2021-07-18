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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePluginConvention;

import net.fabricmc.loom.api.MixinApExtensionAPI;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.GradleMappingContext;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsDependency;

/**
 * This class implements the public extension api.
 */
public abstract class LoomGradleExtensionApiImpl implements LoomGradleExtensionAPI {
	protected final List<LoomDecompiler> decompilers = new ArrayList<>();
	protected final List<JarProcessor> jarProcessors = new ArrayList<>();
	protected final ConfigurableFileCollection log4jConfigs;

	protected File accessWidener = null;
	protected boolean shareCaches = false;
	protected String refmapName = null;
	protected boolean remapMod = true;
	protected String customManifest;

	private NamedDomainObjectContainer<RunConfigSettings> runConfigs;

	protected LoomGradleExtensionApiImpl(Project project, LoomFiles directories) {
		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> new RunConfigSettings(project, baseName));
		this.log4jConfigs = project.files(directories.getDefaultLog4jConfigFile());
	}

	@Override
	public File getAccessWidener() {
		return accessWidener;
	}

	@Override
	public void setAccessWidener(Object file) {
		Objects.requireNonNull(file, "Access widener file cannot be null");
		this.accessWidener = getProject().file(file);
	}

	@Override
	public void setShareCaches(boolean shareCaches) {
		this.shareCaches = shareCaches;
	}

	@Override
	public boolean isShareCaches() {
		return shareCaches;
	}

	@Override
	public List<LoomDecompiler> getDecompilers() {
		return decompilers;
	}

	@Override
	public void addDecompiler(LoomDecompiler decompiler) {
		Objects.requireNonNull(decompiler, "Decompiler cannot be null");
		decompilers.add(decompiler);
	}

	@Override
	public List<JarProcessor> getJarProcessors() {
		return jarProcessors;
	}

	@Override
	public void addJarProcessor(JarProcessor processor) {
		Objects.requireNonNull(processor, "Jar processor cannot be null");
		jarProcessors.add(processor);
	}

	@Override
	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		LayeredMappingSpecBuilder builder = new LayeredMappingSpecBuilder();
		action.execute(builder);
		LayeredMappingSpec builtSpec = builder.build();
		return new LayeredMappingsDependency(new GradleMappingContext(getProject(), "layers_" + builtSpec.getVersion().replace("+", "_").replace(".", "_")), builtSpec, builtSpec.getVersion());
	}

	@Override
	public String getRefmapName() {
		if (refmapName == null || refmapName.isEmpty()) {
			String defaultRefmapName = getProject().getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-refmap.json";
			getProject().getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
			refmapName = defaultRefmapName;
		}

		return refmapName;
	}

	@Override
	public void setRefmapName(String refmapName) {
		this.refmapName = refmapName;
	}

	@Override
	public void setRemapMod(boolean remapMod) {
		this.remapMod = remapMod;
	}

	@Override
	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		action.execute(runConfigs);
	}

	@Override
	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		return runConfigs;
	}

	@Override
	public ConfigurableFileCollection getLog4jConfigs() {
		return log4jConfigs;
	}

	@Override
	public boolean isRemapMod() {
		return remapMod;
	}

	@Override
	public void mixin(Action<MixinApExtensionAPI> action) {
		action.execute(getMixinApExtension());
	}

	@Override
	public void setCustomManifest(String customManifest) {
		Objects.requireNonNull(customManifest, "Custom manifest cannot be null");
		this.customManifest = customManifest;
	}

	@Override
	public String getCustomManifest() {
		return customManifest;
	}

	protected abstract Project getProject();

	protected abstract LoomFiles getFiles();

	protected abstract MixinApExtension getMixinApExtension();

	// This is here to ensure that LoomGradleExtensionApiImpl compiles without any unimplemented methods
	private final class EnsureCompile extends LoomGradleExtensionApiImpl {
		private EnsureCompile() {
			super(null, null);
			throw new RuntimeException();
		}

		@Override
		protected Project getProject() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected LoomFiles getFiles() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected MixinApExtension getMixinApExtension() {
			throw new RuntimeException("Yeah... something is really wrong");
		}
	}
}
