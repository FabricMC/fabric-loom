/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.launch.LaunchProviderSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.forge.FieldMigratedMappingsProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUniversalProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider;
import net.fabricmc.loom.configuration.providers.forge.McpConfigProvider;
import net.fabricmc.loom.configuration.providers.forge.PatchProvider;
import net.fabricmc.loom.configuration.providers.forge.SrgProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.configuration.providers.mappings.MojangMappingsDependency;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.ModPlatform;
import net.fabricmc.loom.util.function.LazyBool;

public class LoomGradleExtension {
	private static final String FORGE_PROPERTY = "loom.forge";
	private static final String PLATFORM_PROPERTY = "loom.platform";
	private static final String INCLUDE_PROPERTY = "loom.forge.include";

	public String refmapName;
	public String loaderLaunchMethod;
	public boolean remapMod = true;
	public String customManifest = null;
	public File accessWidener = null;
	public Function<String, Object> intermediaryUrl = mcVer -> "https://maven.fabricmc.net/net/fabricmc/intermediary/" + mcVer + "/intermediary-" + mcVer + "-v2.jar";
	public boolean shareCaches = false;
	public List<String> mixinConfigs = new ArrayList<>(); // FORGE: Passed to Minecraft
	public boolean useFabricMixin = true; // FORGE: Use Fabric Mixin for better refmap resolutions

	private final ConfigurableFileCollection unmappedMods;
	private final ConfigurableFileCollection log4jConfigs;

	final List<LoomDecompiler> decompilers = new ArrayList<>();
	private final List<JarProcessor> jarProcessors = new ArrayList<>();
	private boolean silentMojangMappingsLicense = false;
	public Boolean generateSrgTiny = null;

	// Not to be set in the build.gradle
	private final Project project;
	private List<String> dataGenMods = new ArrayList<>();
	private LoomDependencyManager dependencyManager;
	private JarProcessorManager jarProcessorManager;
	private JsonObject installerJson;
	private MappingSet[] srcMappingCache = new MappingSet[2];
	private Mercury[] srcMercuryCache = new Mercury[2];
	private ModPlatform platform;
	private final LazyBool supportsInclude;
	private Set<File> mixinMappings = Collections.synchronizedSet(new HashSet<>());
	private final List<String> tasksBeforeRun = Collections.synchronizedList(new ArrayList<>());
	public final List<Supplier<SourceSet>> forgeLocalMods = Collections.synchronizedList(new ArrayList<>(Collections.singletonList(new Supplier<SourceSet>() {
		@Override
		public SourceSet get() {
			return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");
		}
	})));
	@ApiStatus.Experimental
	public final List<Consumer<RunConfig>> settingsPostEdit = new ArrayList<>();

	private NamedDomainObjectContainer<RunConfigSettings> runConfigs;
	private NamedDomainObjectContainer<LaunchProviderSettings> launchConfigs;

	/**
	 * Loom will generate a new genSources task (with a new name, based off of {@link LoomDecompiler#name()})
	 * that uses the specified decompiler instead.
	 */
	public void addDecompiler(LoomDecompiler decompiler) {
		decompilers.add(decompiler);
	}

	/**
	 * Add a transformation over the mapped mc jar.
	 * Adding any jar processor will cause mapped mc jars to be stored per-project so that
	 * different transformation can be applied in different projects.
	 * This means remapping will need to be done individually per-project, which is slower when developing
	 * more than one project using the same minecraft version.
	 */
	public void addJarProcessor(JarProcessor processor) {
		jarProcessors.add(processor);
	}

	public MappingSet getOrCreateSrcMappingCache(int id, Supplier<MappingSet> factory) {
		if (id < 0 || id >= srcMappingCache.length) return factory.get();
		return srcMappingCache[id] != null ? srcMappingCache[id] : (srcMappingCache[id] = factory.get());
	}

	public Mercury getOrCreateSrcMercuryCache(int id, Supplier<Mercury> factory) {
		if (id < 0 || id >= srcMercuryCache.length) return factory.get();
		return srcMercuryCache[id] != null ? srcMercuryCache[id] : (srcMercuryCache[id] = factory.get());
	}

	public void localMods(Action<SourceSetConsumer> action) {
		if (!isForge()) {
			throw new UnsupportedOperationException("Not running with Forge support.");
		}

		action.execute(new SourceSetConsumer());
	}

	public boolean isDataGenEnabled() {
		return isForge() && !dataGenMods.isEmpty();
	}

	public List<String> getDataGenMods() {
		return dataGenMods;
	}

	public class SourceSetConsumer {
		public void add(Object... sourceSets) {
			for (Object sourceSet : sourceSets) {
				if (sourceSet instanceof SourceSet) {
					forgeLocalMods.add(() -> (SourceSet) sourceSet);
				} else {
					forgeLocalMods.add(() -> project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().findByName(String.valueOf(forgeLocalMods)));
				}
			}
		}
	}

	public void dataGen(Action<DataGenConsumer> action) {
		if (!isForge()) {
			throw new UnsupportedOperationException("Not running with Forge support.");
		}

		action.execute(new DataGenConsumer());
	}

	public class DataGenConsumer {
		public void mod(String... modIds) {
			dataGenMods.addAll(Arrays.asList(modIds));

			if (modIds.length > 0 && getRunConfigs().findByName("data") == null) {
				getRunConfigs().create("data", RunConfigSettings::data);
			}
		}
	}

	public void addTaskBeforeRun(String task) {
		this.tasksBeforeRun.add(task);
	}

	public List<String> getTasksBeforeRun() {
		return tasksBeforeRun;
	}

	public void mixinConfig(String... config) {
		mixinConfigs.addAll(Arrays.asList(config));
	}

	public void silentMojangMappingsLicense() {
		this.silentMojangMappingsLicense = true;
	}

	public boolean isSilentMojangMappingsLicenseEnabled() {
		return silentMojangMappingsLicense;
	}

	public Dependency officialMojangMappings() {
		return new MojangMappingsDependency(project, this);
	}

	public LoomGradleExtension(Project project) {
		this.project = project;
		this.unmappedMods = project.files();
		this.supportsInclude = new LazyBool(() -> Boolean.parseBoolean(Objects.toString(project.findProperty(INCLUDE_PROPERTY))));
		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> new RunConfigSettings(project, baseName));
		this.launchConfigs = project.container(LaunchProviderSettings.class,
				baseName -> new LaunchProviderSettings(project, baseName));
		this.log4jConfigs = project.files(getDefaultLog4jConfigFile());
	}

	/**
	 * @see ConfigurableFileCollection#from(Object...)
	 * @deprecated use {@link #getUnmappedModCollection()}{@code .from()} instead
	 */
	@Deprecated
	public void addUnmappedMod(Path file) {
		getUnmappedModCollection().from(file);
	}

	/**
	 * @deprecated use {@link #getUnmappedModCollection()} instead
	 */
	@Deprecated
	public List<Path> getUnmappedMods() {
		return unmappedMods.getFiles().stream()
				.map(File::toPath)
				.collect(Collectors.toList());
	}

	public ConfigurableFileCollection getUnmappedModCollection() {
		return unmappedMods;
	}

	public void setInstallerJson(JsonObject object) {
		this.installerJson = object;
	}

	public JsonObject getInstallerJson() {
		return installerJson;
	}

	public void accessWidener(Object file) {
		this.accessWidener = project.file(file);
	}

	public File getUserCache() {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");

		if (!userCache.exists()) {
			userCache.mkdirs();
		}

		return userCache;
	}

	public File getRootProjectPersistentCache() {
		File projectCache = new File(project.getRootProject().file(".gradle"), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getProjectPersistentCache() {
		File projectCache = new File(project.file(".gradle"), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getRootProjectBuildCache() {
		File projectCache = new File(project.getRootProject().getBuildDir(), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getProjectBuildCache() {
		File projectCache = new File(project.getBuildDir(), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getRemappedModCache() {
		File remappedModCache = new File(getRootProjectPersistentCache(), "remapped_mods");

		if (!remappedModCache.exists()) {
			remappedModCache.mkdir();
		}

		return remappedModCache;
	}

	public File getNestedModCache() {
		File nestedModCache = new File(getRootProjectPersistentCache(), "nested_mods");

		if (!nestedModCache.exists()) {
			nestedModCache.mkdir();
		}

		return nestedModCache;
	}

	public File getNativesJarStore() {
		File natives = new File(getUserCache(), "natives/jars");

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}

	public File getNativesDirectory() {
		if (project.hasProperty("fabric.loom.natives.dir")) {
			return new File((String) project.property("fabric.loom.natives.dir"));
		}

		File natives = new File(getUserCache(), "natives/" + getMinecraftProvider().getMinecraftVersion());

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}

	public boolean hasCustomNatives() {
		return project.getProperties().get("fabric.loom.natives.dir") != null;
	}

	public File getDevLauncherConfig() {
		return new File(getProjectPersistentCache(), "launch.cfg");
	}

	public String getLoaderLaunchMethod() {
		return loaderLaunchMethod != null ? loaderLaunchMethod : "";
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public PatchProvider getPatchProvider() {
		return getDependencyManager().getProvider(PatchProvider.class);
	}

	public MinecraftProvider getMinecraftProvider() {
		return getDependencyManager().getProvider(MinecraftProvider.class);
	}

	public MinecraftMappedProvider getMinecraftMappedProvider() {
		return getMappingsProvider().mappedProvider;
	}

	public MappingsProvider getMappingsProvider() {
		return getDependencyManager().getProvider(isForge() ? FieldMigratedMappingsProvider.class : MappingsProvider.class);
	}

	public McpConfigProvider getMcpConfigProvider() {
		return getDependencyManager().getProvider(McpConfigProvider.class);
	}

	public SrgProvider getSrgProvider() {
		return getDependencyManager().getProvider(SrgProvider.class);
	}

	public ForgeUniversalProvider getForgeUniversalProvider() {
		return getDependencyManager().getProvider(ForgeUniversalProvider.class);
	}

	public ForgeUserdevProvider getForgeUserdevProvider() {
		return getDependencyManager().getProvider(ForgeUserdevProvider.class);
	}

	public ForgeProvider getForgeProvider() {
		return getDependencyManager().getProvider(ForgeProvider.class);
	}

	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}

	public JarProcessorManager getJarProcessorManager() {
		return jarProcessorManager;
	}

	public void setJarProcessorManager(JarProcessorManager jarProcessorManager) {
		this.jarProcessorManager = jarProcessorManager;
	}

	public List<JarProcessor> getJarProcessors() {
		return jarProcessors;
	}

	public String getRefmapName() {
		if (refmapName == null || refmapName.isEmpty()) {
			String defaultRefmapName;

			if (isRootProject()) {
				defaultRefmapName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-refmap.json";
			} else {
				defaultRefmapName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-" + project.getPath().replaceFirst(":", "").replace(':', '_') + "-refmap.json";
			}

			project.getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
			refmapName = defaultRefmapName;
		}

		return refmapName;
	}

	public boolean ideSync() {
		return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
	}

	// Ideally this should use maven, but this is a lot easier
	public Function<String, String> getIntermediaryUrl() {
		// Done like this to work around this possibly not being a java string...
		return s -> intermediaryUrl.apply(s).toString();
	}

	public boolean isRootProject() {
		return project.getRootProject() == project;
	}

	public LoomGradleExtension getRootGradleExtension() {
		if (isRootProject()) {
			return this;
		}

		return project.getRootProject().getExtensions().getByType(LoomGradleExtension.class);
	}

	public LoomGradleExtension getSharedGradleExtension() {
		return isShareCaches() ? getRootGradleExtension() : this;
	}

	public boolean isShareCaches() {
		return shareCaches;
	}

	@ApiStatus.Experimental
	public ModPlatform getPlatform() {
		if (platform == null) {
			Object platformProperty = project.findProperty(PLATFORM_PROPERTY);

			if (platformProperty != null) {
				return platform = ModPlatform.valueOf(Objects.toString(platformProperty).toUpperCase(Locale.ROOT));
			}

			Object forgeProperty = project.findProperty(FORGE_PROPERTY);

			if (forgeProperty != null) {
				project.getLogger().warn("Project " + project.getPath() + " is using property " + FORGE_PROPERTY + " to enable forge mode. Please use '" + PLATFORM_PROPERTY + " = forge' instead!");
				return platform = Boolean.parseBoolean(Objects.toString(forgeProperty)) ? ModPlatform.FORGE : ModPlatform.FABRIC;
			}

			platform = ModPlatform.FABRIC;
		}

		return platform;
	}

	public boolean isForge() {
		return getPlatform() == ModPlatform.FORGE;
	}

	public boolean supportsInclude() {
		return !isForge() || supportsInclude.getAsBoolean();
	}

	public boolean shouldGenerateSrgTiny() {
		if (generateSrgTiny != null) {
			return generateSrgTiny;
		}

		return isForge();
	}

	// Creates a new file each time its called, this is then held onto later when remapping the output jar
	// Required as now when using parallel builds the old single file could be written by another sourceset compile task
	public synchronized File getNextMixinMappings() {
		File mixinMapping = new File(getProjectBuildCache(), "mixin-map-" + getMinecraftProvider().getMinecraftVersion() + "-" + getMappingsProvider().mappingsVersion + "." + mixinMappings.size() + ".tiny");
		mixinMappings.add(mixinMapping);
		return mixinMapping;
	}

	public Set<File> getAllMixinMappings() {
		return Collections.unmodifiableSet(mixinMappings);
	}

	public List<LoomDecompiler> getDecompilers() {
		return decompilers;
	}

	public File getDefaultLog4jConfigFile() {
		return new File(getProjectPersistentCache(), "log4j.xml");
	}

	public File getUnpickLoggingConfigFile() {
		return new File(getProjectPersistentCache(), "unpick-logging.properties");
	}

	public ConfigurableFileCollection getLog4jConfigs() {
		return log4jConfigs;
	}

	@ApiStatus.Experimental
	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		action.execute(runConfigs);
	}

	@ApiStatus.Experimental
	public void launches(Action<NamedDomainObjectContainer<LaunchProviderSettings>> action) {
		action.execute(launchConfigs);
	}

	@ApiStatus.Experimental
	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		return runConfigs;
	}

	@ApiStatus.Experimental
	public NamedDomainObjectContainer<LaunchProviderSettings> getLaunchConfigs() {
		return launchConfigs;
	}
}
