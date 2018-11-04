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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loom.task.DownloadTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModRemapper;
import net.fabricmc.loom.util.Version;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.ide.api.GeneratorTask;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class AbstractPlugin implements Plugin<Project> {
	protected Project project;

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
		readModJson(extension);

		// Force add Mojang repository
		addMavenRepo(target, "Mojang", "https://libraries.minecraft.net/");

		// Minecraft libraries configuration
		project.getConfigurations().maybeCreate(Constants.CONFIG_MINECRAFT);
		project.getConfigurations().maybeCreate(Constants.CONFIG_MC_DEPENDENCIES);
		project.getConfigurations().maybeCreate(Constants.CONFIG_MC_DEPENDENCIES_CLIENT);
		project.getConfigurations().maybeCreate(Constants.CONFIG_NATIVES);
		Configuration compileModsConfig = project.getConfigurations().maybeCreate(Constants.COMPILE_MODS);

		compileModsConfig.setTransitive(false); //Dont get transitive deps of mods

		// Common libraries extends from client libraries, CONFIG_MC_DEPENDENCIES will contains all MC dependencies
		project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT).extendsFrom(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES).extendsFrom(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES_CLIENT)));

		configureIDEs();
		configureCompile();

		Map<Project, Set<Task>> taskMap = project.getAllTasks(true);
		for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
			Project project = entry.getKey();
			Set<Task> taskSet = entry.getValue();
			for (Task task : taskSet) {
				if (task instanceof GeneratorTask) {
					task.dependsOn("setup");
				}

				if (task instanceof JavaCompile
					&& !(task.getName().contains("Test")) && !(task.getName().contains("test"))) {
					JavaCompile javaCompileTask = (JavaCompile) task;
					javaCompileTask.doFirst(task1 -> {
						project.getLogger().lifecycle(":setting java compiler args");
						try {
							javaCompileTask.getClasspath().add(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT));
							javaCompileTask.getClasspath().add(target.files(this.getClass().getProtectionDomain().getCodeSource().getLocation()));

							javaCompileTask.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + Constants.MAPPINGS_TINY.get(extension).getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + Constants.MAPPINGS_MIXIN_EXPORT.get(extension).getCanonicalPath());
							if(extension.refmapName == null || extension.refmapName.isEmpty()){
								project.getLogger().error("Could not find refmap definition, will be using default name: " + project.getName() + "-refmap.json");
								extension.refmapName = project.getName() + "-refmap.json";
							}
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(javaCompileTask.getDestinationDir(), extension.refmapName).getCanonicalPath());
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
		ideaModel.getModule().getScopes().get("COMPILE").get("plus").add(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT));

		// ECLIPSE
		EclipseModel eclipseModel = (EclipseModel) project.getExtensions().getByName("eclipse");
		eclipseModel.getClasspath().getPlusConfigurations().add(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT));
	}

	/**
	 * Add Minecraft dependencies to compile time
	 */
	protected void configureCompile() {
		JavaPluginConvention javaModule = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		SourceSet test = javaModule.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

		main.setCompileClasspath(main.getCompileClasspath().plus(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT)));
		test.setCompileClasspath(test.getCompileClasspath().plus(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT)));
		main.setRuntimeClasspath(main.getCompileClasspath().plus(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT)));
		test.setCompileClasspath(test.getCompileClasspath().plus(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT)));

		Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		project.afterEvaluate(project1 -> {
			LoomGradleExtension extension = project1.getExtensions().getByType(LoomGradleExtension.class);

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getUserCache());
				flatDirectoryArtifactRepository.setName("UserCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(Constants.CACHE_FILES.get(extension));
				flatDirectoryArtifactRepository.setName("UserLocalCacheFiles");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("modmuss50");
				mavenArtifactRepository.setUrl("https://maven.modmuss50.me/");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("SpongePowered");
				mavenArtifactRepository.setUrl("http://repo.spongepowered.org/maven");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Mojang");
				mavenArtifactRepository.setUrl("https://libraries.minecraft.net/");
			});

			project1.getRepositories().mavenCentral();
			project1.getRepositories().jcenter();

			Gson gson = new Gson();
			try {
				DownloadTask.downloadMcJson(extension, project1.getLogger());
				Version version = gson.fromJson(new FileReader(Constants.MINECRAFT_JSON.get(extension)), Version.class);
				for (Version.Library library : version.libraries) {
					if (library.allowed() && library.getFile(extension) != null) {
						String configName = Constants.CONFIG_MC_DEPENDENCIES;
						if (library.name.contains("java3d") || library.name.contains("paulscode") || library.name.contains("lwjgl") || library.name.contains("twitch") || library.name.contains("jinput") || library.name.contains("text2speech") || library.name.contains("objc")) {
							configName = Constants.CONFIG_MC_DEPENDENCIES_CLIENT;
						}
						project1.getDependencies().add(configName, library.getArtifactName());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			project1.getDependencies().add(Constants.CONFIG_MINECRAFT, "net.minecraft:" + Constants.MINECRAFT_FINAL_JAR.get(extension).getName().replace(".jar", ""));

			if (extension.isModWorkspace()) {
				//only add this when not in a dev env
				project1.getDependencies().add(Constants.COMPILE_MODS, "net.fabricmc:fabric-loader:" + extension.getVersionString());
			}


		});

		project.getTasks().getByName("jar").doLast(task -> {
			project.getLogger().lifecycle(":remapping mods");
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
			try {
				if(extension.hasPomf()){
					ModRemapper.remap(project);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		project.afterEvaluate(project12 -> {
			project12.getTasks().getByName("idea").dependsOn(project12.getTasks().getByName("cleanIdea")).dependsOn(project12.getTasks().getByName("setup")).dependsOn(project12.getTasks().getByName("extractNatives"));
			project12.getTasks().getByName("idea").finalizedBy(project12.getTasks().getByName("genIdeaWorkspace"));
		});

	}

	protected void readModJson(LoomGradleExtension extension) {
		File resDir = new File(project.getProjectDir(), "src" + File.separator + "main" + File.separator + "resources");
		File modJson = new File(resDir, "mod.json");
		if (modJson.exists()) {
			Gson gson = new Gson();
			try {
				JsonElement jsonElement = gson.fromJson(new FileReader(modJson), JsonElement.class);
				JsonObject jsonObject = jsonElement.getAsJsonObject();
				if ((extension.version == null || extension.version.isEmpty()) && jsonObject.has("version")) {
					project.setVersion(jsonObject.get("version").getAsString());
				}
				if (jsonObject.has("group")) {
					project.setGroup(jsonObject.get("group").getAsString());
				}
				if (jsonObject.has("description")) {
					project.setDescription(jsonObject.get("description").getAsString());
				}
				//TODO load deps

			} catch (FileNotFoundException e) {
				//This wont happen as we have checked for it
				e.printStackTrace();
			}
		}
	}
}
