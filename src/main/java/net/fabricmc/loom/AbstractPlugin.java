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

import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import net.fabricmc.loom.providers.*;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.RemapAllSourcesTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.*;
import net.fabricmc.loom.util.mixin.JavaApInvoker;
import net.fabricmc.loom.util.mixin.KaptApInvoker;
import net.fabricmc.loom.util.mixin.ScalaApInvoker;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.IOException;
import java.util.*;

public class AbstractPlugin implements Plugin<Project> {
	protected Project project;

	public static boolean isRootProject(Project project) {
		return project.getRootProject() == project;
	}

	private void extendsFrom(String a, String b) {
		project.getConfigurations().getByName(a).extendsFrom(project.getConfigurations().getByName(b));
	}

	@Override
	public void apply(Project target) {
		this.project = target;

		project.getLogger().lifecycle("Fabric Loom: " + AbstractPlugin.class.getPackage().getImplementationVersion());

		boolean refreshDeps = project.getGradle().getStartParameter().isRefreshDependencies();
		DownloadUtil.refreshDeps = refreshDeps;

		if (refreshDeps) {
			MappingsCache.INSTANCE.invalidate();
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
		project.getExtensions().add("loom", project.getExtensions().getByName("minecraft"));
		project.getExtensions().create("fabricApi", FabricApiExtension.class, project);

		// Force add Mojang and Forge repositories
		addMavenRepo(target, "Mojang", "https://libraries.minecraft.net/");
		addMavenRepo(target, "Forge", "https://files.minecraftforge.net/maven/");

		Configuration modCompileClasspathConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH);
		modCompileClasspathConfig.setTransitive(true);
		Configuration modCompileClasspathMappedConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED);
		modCompileClasspathMappedConfig.setTransitive(false);

		Configuration minecraftNamedConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT_NAMED);
		minecraftNamedConfig.setTransitive(false); // The launchers do not recurse dependencies
		Configuration minecraftDependenciesConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT_DEPENDENCIES);
		minecraftDependenciesConfig.setTransitive(false);
		Configuration minecraftConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MINECRAFT);
		minecraftConfig.setTransitive(false);

		if (project.getExtensions().getByType(LoomGradleExtension.class).isForge()) {
			Configuration forgeConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE);
			forgeConfig.setTransitive(false);
			Configuration forgeUserdevConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_USERDEV);
			forgeUserdevConfig.setTransitive(false);
			Configuration forgeInstallerConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_INSTALLER);
			forgeInstallerConfig.setTransitive(false);
			Configuration forgeUniversalConfig = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_UNIVERSAL);
			forgeUniversalConfig.setTransitive(false);
			Configuration forgeDependencies = project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_DEPENDENCIES);
			forgeDependencies.setTransitive(false);
			Configuration mcpConfig = project.getConfigurations().maybeCreate(Constants.Configurations.MCP_CONFIG);
			mcpConfig.setTransitive(false);

			extendsFrom(Constants.Configurations.MINECRAFT_DEPENDENCIES, Constants.Configurations.FORGE_DEPENDENCIES);
		}

		Configuration includeConfig = project.getConfigurations().maybeCreate(Constants.Configurations.INCLUDE);
		includeConfig.setTransitive(false); // Dont get transitive deps

		project.getConfigurations().maybeCreate(Constants.Configurations.MAPPINGS);
		project.getConfigurations().maybeCreate(Constants.Configurations.MAPPINGS_FINAL);

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			Configuration compileModsConfig = project.getConfigurations().maybeCreate(entry.getSourceConfiguration());
			compileModsConfig.setTransitive(true);
			Configuration compileModsMappedConfig = project.getConfigurations().maybeCreate(entry.getRemappedConfiguration());
			compileModsMappedConfig.setTransitive(false); // Don't get transitive deps of already remapped mods

			extendsFrom(entry.getTargetConfiguration(project.getConfigurations()), entry.getRemappedConfiguration());

			if (entry.isOnModCompileClasspath()) {
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, entry.getSourceConfiguration());
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, entry.getRemappedConfiguration());
			}
		}

		extendsFrom("compileClasspath", Constants.Configurations.MINECRAFT_NAMED);
		extendsFrom("runtimeClasspath", Constants.Configurations.MINECRAFT_NAMED);
		extendsFrom("testCompileClasspath", Constants.Configurations.MINECRAFT_NAMED);
		extendsFrom("testRuntimeClasspath", Constants.Configurations.MINECRAFT_NAMED);

		extendsFrom(Constants.Configurations.MINECRAFT_NAMED, Constants.Configurations.MINECRAFT_DEPENDENCIES);

		extendsFrom("compile", Constants.Configurations.MAPPINGS_FINAL);

		configureIDEs();
		configureCompile();
		configureMaven();
	}

	public Project getProject() {
		return project;
	}

	/**
	 * Permit to add a Maven repository to a target project.
	 *
	 * @param target The target project
	 * @param name   The name of the repository
	 * @param url    The URL of the repository
	 * @return An object containing the name and the URL of the repository that can be modified later
	 */
	public MavenArtifactRepository addMavenRepo(Project target, final String name, final String url) {
		return target.getRepositories().maven(repo -> {
			repo.setName(name);
			repo.setUrl(url);
		});
	}

	/**
	 * Add Minecraft dependencies to IDE dependencies.
	 */
	protected void configureIDEs() {
		// IDEA
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");

		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		ideaModel.getModule().setInheritOutputDirs(true);
	}

	/**
	 * Add Minecraft dependencies to compile time.
	 */
	protected void configureCompile() {
		JavaPluginConvention javaModule = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		project.afterEvaluate(project1 -> {
			LoomGradleExtension extension = project1.getExtensions().getByType(LoomGradleExtension.class);

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRootProjectBuildCache());
				flatDirectoryArtifactRepository.setName("UserLocalCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRemappedModCache());
				flatDirectoryArtifactRepository.setName("UserLocalRemappedMods");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Fabric");
				mavenArtifactRepository.setUrl("https://maven.fabricmc.net/");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Mojang");
				mavenArtifactRepository.setUrl("https://libraries.minecraft.net/");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Forge");
				mavenArtifactRepository.setUrl("https://files.minecraftforge.net/maven/");
			});

			project1.getRepositories().mavenCentral();
			project1.getRepositories().jcenter();

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			if (extension.isForge()) {
				dependencyManager.addProvider(new ForgeProvider(getProject()));
				dependencyManager.addProvider(new ForgeUserdevProvider(getProject()));
				dependencyManager.addProvider(new McpConfigProvider(getProject()));
				dependencyManager.addProvider(new PatchProvider(getProject()));
				dependencyManager.addProvider(new ForgeUniversalProvider(getProject()));
			}

			dependencyManager.addProvider(new MinecraftProvider(getProject()));
			dependencyManager.addProvider(new MappingsProvider(getProject()));
			dependencyManager.addProvider(new LaunchProvider(getProject()));

			dependencyManager.handleDependencies(project1);

			project1.getTasks().getByName("idea").finalizedBy(project1.getTasks().getByName("genIdeaWorkspace"));
			project1.getTasks().getByName("eclipse").finalizedBy(project1.getTasks().getByName("genEclipseRuns"));
			project1.getTasks().getByName("cleanEclipse").finalizedBy(project1.getTasks().getByName("cleanEclipseRuns"));

			if (extension.autoGenIDERuns) {
				SetupIntelijRunConfigs.setup(project1);
			}

			// Enables the default mod remapper
			if (extension.remapMod) {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				RemapJarTask remapJarTask = (RemapJarTask) project1.getTasks().findByName("remapJar");

				assert remapJarTask != null;

				if (!remapJarTask.getInput().isPresent()) {
					jarTask.setClassifier("dev");
					remapJarTask.setClassifier("");
					remapJarTask.getInput().set(jarTask.getArchivePath());
				}

				if (extension.isForge()) {
					remapJarTask.getToM().set("srg");
					((Jar) jarTask).manifest(manifest -> {
						List<String> configs = new ArrayList<>();
						if (extension.mixinConfig != null) {
							configs.add(extension.mixinConfig);
						}
						if (extension.mixinConfigs != null) {
							configs.addAll(extension.mixinConfigs);
						}
						manifest.attributes(ImmutableMap.of("MixinConfigs", String.join(",", configs)));
					});
				}

				extension.getUnmappedModCollection().from(jarTask);
				remapJarTask.getAddNestedDependencies().set(true);
				remapJarTask.getRemapAccessWidener().set(true);

				project1.getArtifacts().add("archives", remapJarTask);
				remapJarTask.dependsOn(jarTask);
				project1.getTasks().getByName("build").dependsOn(remapJarTask);

				Map<Project, Set<Task>> taskMap = project.getAllTasks(true);

				for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
					Set<Task> taskSet = entry.getValue();

					for (Task task : taskSet) {
						if (task instanceof RemapJarTask && ((RemapJarTask) task).getAddNestedDependencies().getOrElse(false)) {
							//Run all the sub project remap jars tasks before the root projects jar, this is to allow us to include projects
							NestedJars.getRequiredTasks(project1).forEach(task::dependsOn);
						}
					}
				}

				SourceRemapper remapper = null;
				Task parentTask = project1.getTasks().getByName("build");

				if (extension.isShareCaches()) {
					Project rootProject = project.getRootProject();

					if (extension.isRootProject()) {
						SourceRemapper sourceRemapper = new SourceRemapper(rootProject, false);
						JarRemapper jarRemapper = new JarRemapper();

						remapJarTask.jarRemapper = jarRemapper;

						rootProject.getTasks().register("remapAllSources", RemapAllSourcesTask.class, task -> {
							task.sourceRemapper = sourceRemapper;
							task.doLast(t -> sourceRemapper.remapAll());
						});

						parentTask = rootProject.getTasks().getByName("remapAllSources");

						rootProject.getTasks().register("remapAllJars", AbstractLoomTask.class, task -> {
							task.doLast(t -> {
								try {
									jarRemapper.remap();
								} catch (IOException e) {
									throw new RuntimeException("Failed to remap jars", e);
								}
							});
						});
					} else {
						parentTask = rootProject.getTasks().getByName("remapAllSources");
						remapper = ((RemapAllSourcesTask) parentTask).sourceRemapper;

						remapJarTask.jarRemapper = ((RemapJarTask) rootProject.getTasks().getByName("remapJar")).jarRemapper;

						project1.getTasks().getByName("build").dependsOn(parentTask);
						project1.getTasks().getByName("build").dependsOn(rootProject.getTasks().getByName("remapAllJars"));
						rootProject.getTasks().getByName("remapAllJars").dependsOn(project1.getTasks().getByName("remapJar"));
					}
				}

				try {
					AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project1.getTasks().getByName("sourcesJar");

					RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project1.getTasks().findByName("remapSourcesJar");
					remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
					remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
					remapSourcesJarTask.doLast(task -> project1.getArtifacts().add("archives", remapSourcesJarTask.getOutput()));
					remapSourcesJarTask.dependsOn(project1.getTasks().getByName("sourcesJar"));

					if (extension.isShareCaches()) {
						remapSourcesJarTask.setSourceRemapper(remapper);
					}

					parentTask.dependsOn(remapSourcesJarTask);
				} catch (UnknownTaskException ignored) {
					// pass
				}
			} else {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				extension.getUnmappedModCollection().from(jarTask);
			}

			// Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
			System.setProperty("log4j2.disable.jmx", "true");
			System.setProperty("log4j.shutdownHookEnabled", "false");

			project.getLogger().info("Configuring compiler arguments for Java");
			new JavaApInvoker(project).configureMixin();

			if (project.getPluginManager().hasPlugin("scala")) {
				project.getLogger().info("Configuring compiler arguments for Scala");
				new ScalaApInvoker(project).configureMixin();
			}

			if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
				project.getLogger().info("Configuring compiler arguments for Kapt plugin");
				new KaptApInvoker(project).configureMixin();
			}
		});

		if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	protected void configureMaven() {
		project.afterEvaluate((p) -> {
			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				if (!entry.hasMavenScope()) {
					continue;
				}

				Configuration compileModsConfig = p.getConfigurations().getByName(entry.getSourceConfiguration());

				// add modsCompile to maven-publish
				PublishingExtension mavenPublish = p.getExtensions().findByType(PublishingExtension.class);

				if (mavenPublish != null) {
					mavenPublish.publications((publications) -> {
						for (Publication publication : publications) {
							if (publication instanceof MavenPublication) {
								((MavenPublication) publication).pom((pom) -> pom.withXml((xml) -> {
									Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
									Set<String> foundArtifacts = new HashSet<>();

									GroovyXmlUtil.childrenNodesStream(dependencies).filter((n) -> "dependency".equals(n.name())).forEach((n) -> {
										Optional<Node> groupId = GroovyXmlUtil.getNode(n, "groupId");
										Optional<Node> artifactId = GroovyXmlUtil.getNode(n, "artifactId");

										if (groupId.isPresent() && artifactId.isPresent()) {
											foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
										}
									});

									for (Dependency dependency : compileModsConfig.getAllDependencies()) {
										if (foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
											continue;
										}

										Node depNode = dependencies.appendNode("dependency");
										depNode.appendNode("groupId", dependency.getGroup());
										depNode.appendNode("artifactId", dependency.getName());
										depNode.appendNode("version", dependency.getVersion());
										depNode.appendNode("scope", entry.getMavenScope());

										if (dependency instanceof ModuleDependency) {
											final Set<ExcludeRule> exclusions = ((ModuleDependency) dependency).getExcludeRules();

											if (!exclusions.isEmpty()) {
												Node exclusionsNode = depNode.appendNode("exclusions");

												for (ExcludeRule rule : exclusions) {
													Node exclusionNode = exclusionsNode.appendNode("exclusion");
													exclusionNode.appendNode("groupId", rule.getGroup() == null ? "*" : rule.getGroup());
													exclusionNode.appendNode("artifactId", rule.getModule() == null ? "*" : rule.getModule());
												}
											}
										}
									}
								}));
							}
						}
					});
				}
			}
		});
	}
}
