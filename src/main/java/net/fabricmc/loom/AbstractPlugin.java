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
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.providers.ModRemapperProvider;
import net.fabricmc.loom.task.RemapJar;
import net.fabricmc.loom.task.RemapSourcesJar;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.SetupIntelijRunConfigs;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class AbstractPlugin implements Plugin<Project> {
	protected Project project;

	private void extendsFrom(String a, String b) {
		project.getConfigurations().getByName(a).extendsFrom(project.getConfigurations().getByName(b));
	}

	@Override
	public void apply(Project target) {
		this.project = target;

		project.getLogger().lifecycle("Fabric Loom: " + AbstractPlugin.class.getPackage().getImplementationVersion());

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		// Force add Mojang repository
		addMavenRepo(target, "Mojang", "https://libraries.minecraft.net/");

		Configuration compileModsConfig = project.getConfigurations().maybeCreate(Constants.COMPILE_MODS);
		compileModsConfig.setTransitive(false); // Dont get transitive deps of mods
		Configuration compileModsMappedConfig = project.getConfigurations().maybeCreate(Constants.COMPILE_MODS_MAPPED);
		compileModsMappedConfig.setTransitive(false); // Dont get transitive deps of mods
		Configuration minecraftNamedConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_NAMED);
		minecraftNamedConfig.setTransitive(false); // The launchers do not recurse dependencies
		Configuration minecraftIntermediaryConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_INTERMEDIARY);
		minecraftIntermediaryConfig.setTransitive(false);
		Configuration minecraftDependenciesConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_DEPENDENCIES);
		minecraftDependenciesConfig.setTransitive(false);
		Configuration minecraftConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT);
		minecraftConfig.setTransitive(false);

		project.getConfigurations().maybeCreate(Constants.MAPPINGS);

		configureIDEs();
		configureCompile();

		extendsFrom(Constants.MINECRAFT_NAMED, Constants.MINECRAFT_DEPENDENCIES);
		extendsFrom(Constants.MINECRAFT_INTERMEDIARY, Constants.MINECRAFT_DEPENDENCIES);

        extendsFrom(Constants.COMPILE_MODS_MAPPED, Constants.MINECRAFT_NAMED);
        extendsFrom("compile", Constants.COMPILE_MODS_MAPPED);
        extendsFrom("compile", Constants.MAPPINGS);
        extendsFrom("annotationProcessor", Constants.COMPILE_MODS_MAPPED);
        extendsFrom("annotationProcessor", Constants.MAPPINGS);

		Map<Project, Set<Task>> taskMap = project.getAllTasks(true);
		for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
			Project project = entry.getKey();
			Set<Task> taskSet = entry.getValue();
			for (Task task : taskSet) {
				if (task instanceof JavaCompile
					&& !(task.getName().contains("Test")) && !(task.getName().contains("test"))) {
					JavaCompile javaCompileTask = (JavaCompile) task;
					javaCompileTask.doFirst(task1 -> {
						project.getLogger().lifecycle(":setting java compiler args");
						try {
							javaCompileTask.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + extension.getMappingsProvider().MAPPINGS_TINY.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + extension.getMappingsProvider().MAPPINGS_MIXIN_EXPORT.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(javaCompileTask.getDestinationDir(), extension.getRefmapName()).getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AdefaultObfuscationEnv=named:intermediary");
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			}

		}

	}

	/**
	 * Permit to create a Task instance of the type in the project
	 *
	 * @param name The name of the task
	 * @param type The type of the task that will be used to create an instance
	 * @return The created task object for the project
	 */
	public <T extends Task> T makeTask(String name, Class<T> type) {
		return makeTask(project, name, type);
	}

	/**
	 * Permit to create a Task instance of the type in a project
	 *
	 * @param target The target project
	 * @param name The name of the task
	 * @param type The type of the task that will be used to create an instance
	 * @return The created task object for the specified project
	 */
	public static <T extends Task> T makeTask(Project target, String name, Class<T> type) {
		return target.getTasks().create(name, type);
	}

	/**
	 * Permit to add a Maven repository to a target project
	 *
	 * @param target The garget project
	 * @param name The name of the repository
	 * @param url The URL of the repository
	 * @return An object containing the name and the URL of the repository that can be modified later
	 */
	public MavenArtifactRepository addMavenRepo(Project target, final String name, final String url) {
		return target.getRepositories().maven(repo -> {
			repo.setName(name);
			repo.setUrl(url);
		});
	}

	/**
	 * Add Minecraft dependencies to IDE dependencies
	 */
	protected void configureIDEs() {
		// IDEA
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");

		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		ideaModel.getModule().setInheritOutputDirs(true);

		// ECLIPSE
		EclipseModel eclipseModel = (EclipseModel) project.getExtensions().getByName("eclipse");
	}

	/**
	 * Add Minecraft dependencies to compile time
	 */
	protected void configureCompile() {
		JavaPluginConvention javaModule = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		SourceSet test = javaModule.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		project.afterEvaluate(project1 -> {
			LoomGradleExtension extension = project1.getExtensions().getByType(LoomGradleExtension.class);

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getUserCache());
				flatDirectoryArtifactRepository.setName("UserCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getProjectCache());
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

			/* project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("SpongePowered");
				mavenArtifactRepository.setUrl("http://repo.spongepowered.org/maven");
			}); */

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Mojang");
				mavenArtifactRepository.setUrl("https://libraries.minecraft.net/");
			});

			project1.getRepositories().mavenCentral();
			project1.getRepositories().jcenter();

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			dependencyManager.addProvider(new MinecraftProvider());
			dependencyManager.addProvider(new MappingsProvider());
			dependencyManager.addProvider(new ModRemapperProvider());

			dependencyManager.handleDependencies(project1);

			project1.getTasks().getByName("idea").finalizedBy(project1.getTasks().getByName("genIdeaWorkspace"));
			project1.getTasks().getByName("eclipse").finalizedBy(project1.getTasks().getByName("genEclipseRuns"));

			if(extension.autoGenIDERuns){
				SetupIntelijRunConfigs.setup(project1);
			}

			// add dependencies for mixin annotation processor
			DependencyHandler handler = project1.getDependencies();
			handler.add("annotationProcessor", "net.fabricmc:sponge-mixin:" + extension.getMixinVersion());
			handler.add("annotationProcessor", "net.fabricmc:fabric-loom:" + extension.getLoomVersion());

			// Enables the default mod remapper
			if (extension.remapMod) {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");

				RemapJar remapJarTask = (RemapJar) project1.getTasks().findByName("remapJar");
				if (remapJarTask.jar==null) remapJarTask.jar = jarTask.getArchivePath();
				remapJarTask.doLast(task -> project1.getArtifacts().add("archives", remapJarTask.jar));
				remapJarTask.dependsOn(project1.getTasks().getByName("jar"));
				project1.getTasks().getByName("build").dependsOn(remapJarTask);

				try {
					AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project1.getTasks().getByName("sourcesJar");

					RemapSourcesJar remapSourcesJarTask = (RemapSourcesJar) project1.getTasks().findByName("remapSourcesJar");
					remapSourcesJarTask.jar = sourcesTask.getArchivePath();
					remapSourcesJarTask.doLast(task -> project1.getArtifacts().add("archives", remapSourcesJarTask.jar));
					remapSourcesJarTask.dependsOn(project1.getTasks().getByName("sourcesJar"));
					project1.getTasks().getByName("build").dependsOn(remapSourcesJarTask);
				} catch (UnknownTaskException e) {
					// pass
				}
			} else {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				extension.addUnmappedMod(jarTask.getArchivePath());
			}
		});
	}
}
