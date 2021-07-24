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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

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
	protected final ListProperty<LoomDecompiler> decompilers;
	protected final ListProperty<JarProcessor> jarProcessors;
	protected final ConfigurableFileCollection log4jConfigs;
	protected final RegularFileProperty accessWidener;
	protected final Property<Boolean> shareCaches;
	protected final Property<String> refmapName;
	protected final Property<Boolean> remapArchives;
	protected final Property<String> customManifest;

	private NamedDomainObjectContainer<RunConfigSettings> runConfigs;

	protected LoomGradleExtensionApiImpl(Project project, LoomFiles directories) {
		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> new RunConfigSettings(project, baseName));
		this.decompilers = project.getObjects().listProperty(LoomDecompiler.class)
				.empty();
		this.jarProcessors = project.getObjects().listProperty(JarProcessor.class)
				.empty();
		this.log4jConfigs = project.files(directories.getDefaultLog4jConfigFile());
		this.accessWidener = project.getObjects().fileProperty();
		this.shareCaches = project.getObjects().property(Boolean.class)
				.convention(false);
		this.refmapName = project.getObjects().property(String.class)
				.convention(project.provider(this::getDefaultMixinRefmapName));
		this.remapArchives = project.getObjects().property(Boolean.class)
				.convention(true);
		this.customManifest = project.getObjects().property(String.class);
	}

	@Override
	public RegularFileProperty getAccessWidenerPath() {
		return accessWidener;
	}

	@Override
	public void setAccessWidener(Object file) {
		getAccessWidenerPath().set(getProject().file(file));
	}

	@Override
	public Property<Boolean> getShareCaches() {
		return shareCaches;
	}

	@Override
	public ListProperty<LoomDecompiler> getLoomDecompilers() {
		return decompilers;
	}

	@Override
	public ListProperty<JarProcessor> getMinecraftJarProcessors() {
		return jarProcessors;
	}

	@Override
	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		LayeredMappingSpecBuilder builder = new LayeredMappingSpecBuilder();
		action.execute(builder);
		LayeredMappingSpec builtSpec = builder.build();
		return new LayeredMappingsDependency(new GradleMappingContext(getProject(), "layers_" + builtSpec.getVersion().replace("+", "_").replace(".", "_")), builtSpec, builtSpec.getVersion());
	}

	@Override
	public Property<String> getMixinRefmapName() {
		return refmapName;
	}

	public String getDefaultMixinRefmapName() {
		String defaultRefmapName = getProject().getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-refmap.json";
		getProject().getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
		return defaultRefmapName;
	}

	@Override
	public Property<Boolean> getRemapArchives() {
		return remapArchives;
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
	public void mixin(Action<MixinApExtensionAPI> action) {
		action.execute(getMixinApExtension());
	}

	@Override
	public Property<String> getCustomMinecraftManifest() {
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
