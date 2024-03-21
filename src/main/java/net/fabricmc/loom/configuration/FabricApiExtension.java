/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2023 FabricMC
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

package net.fabricmc.loom.configuration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.download.DownloadException;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class FabricApiExtension {
	@Inject
	public abstract Project getProject();

	private static final String DATAGEN_SOURCESET_NAME = "datagen";

	private static final HashMap<String, Map<String, String>> moduleVersionCache = new HashMap<>();
	private static final HashMap<String, Map<String, String>> deprecatedModuleVersionCache = new HashMap<>();

	public Dependency module(String moduleName, String fabricApiVersion) {
		return getProject().getDependencies()
				.create(getDependencyNotation(moduleName, fabricApiVersion));
	}

	public String moduleVersion(String moduleName, String fabricApiVersion) {
		String moduleVersion = moduleVersionCache
				.computeIfAbsent(fabricApiVersion, this::getApiModuleVersions)
				.get(moduleName);

		if (moduleVersion == null) {
			moduleVersion = deprecatedModuleVersionCache
					.computeIfAbsent(fabricApiVersion, this::getDeprecatedApiModuleVersions)
					.get(moduleName);
		}

		if (moduleVersion == null) {
			throw new RuntimeException("Failed to find module version for module: " + moduleName);
		}

		return moduleVersion;
	}

	/**
	 * Configure data generation with the default options.
	 */
	public void configureDataGeneration() {
		configureDataGeneration(dataGenerationSettings -> { });
	}

	/**
	 * Configure data generation with custom options.
	 */
	public void configureDataGeneration(Action<DataGenerationSettings> action) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final TaskContainer taskContainer = getProject().getTasks();

		DataGenerationSettings settings = getProject().getObjects().newInstance(DataGenerationSettings.class);
		settings.getOutputDirectory().set(getProject().file("src/main/generated"));
		settings.getCreateRunConfiguration().convention(true);
		settings.getCreateSourceSet().convention(false);
		settings.getStrictValidation().convention(false);
		settings.getAddToResources().convention(true);

		action.execute(settings);

		final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(getProject());
		final File outputDirectory = settings.getOutputDirectory().getAsFile().get();

		if (settings.getAddToResources().get()) {
			mainSourceSet.resources(files -> {
				// Add the src/main/generated to the main sourceset's resources.
				Set<File> srcDirs = new HashSet<>(files.getSrcDirs());
				srcDirs.add(outputDirectory);
				files.setSrcDirs(srcDirs);
			});
		}

		// Exclude the cache dir from the output jar to ensure reproducibility.
		taskContainer.getByName(JavaPlugin.JAR_TASK_NAME, task -> {
			Jar jar = (Jar) task;
			jar.exclude(".cache/**");
		});

		taskContainer.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME, task -> {
			Delete clean = (Delete) task;
			clean.delete(outputDirectory);
		});

		if (settings.getCreateSourceSet().get()) {
			SourceSetContainer sourceSets = SourceSetHelper.getSourceSets(getProject());

			// Create the new datagen sourceset, depend on the main sourceset.
			SourceSet dataGenSourceSet = sourceSets.create(DATAGEN_SOURCESET_NAME, sourceSet -> {
				sourceSet.setCompileClasspath(
							sourceSet.getCompileClasspath()
								.plus(mainSourceSet.getOutput())
				);

				sourceSet.setRuntimeClasspath(
							sourceSet.getRuntimeClasspath()
									.plus(mainSourceSet.getOutput())
				);

				extendsFrom(getProject(), sourceSet.getCompileClasspathConfigurationName(), mainSourceSet.getCompileClasspathConfigurationName());
				extendsFrom(getProject(), sourceSet.getRuntimeClasspathConfigurationName(), mainSourceSet.getRuntimeClasspathConfigurationName());
			});

			settings.getModId().convention(getProject().provider(() -> {
				try {
					final FabricModJson fabricModJson = FabricModJsonFactory.createFromSourceSetsNullable(dataGenSourceSet);

					if (fabricModJson == null) {
						throw new RuntimeException("Could not find a fabric.mod.json file in the data source set or a value for DataGenerationSettings.getModId()");
					}

					return fabricModJson.getId();
				} catch (IOException e) {
					throw new org.gradle.api.UncheckedIOException("Failed to read mod id from the datagen source set.", e);
				}
			}));

			extension.getMods().create(settings.getModId().get(), mod -> {
				// Create a classpath group for this mod. Assume that the main sourceset is already in a group.
				mod.sourceSet(DATAGEN_SOURCESET_NAME);
			});

			extension.createRemapConfigurations(sourceSets.getByName(DATAGEN_SOURCESET_NAME));
		}

		if (settings.getCreateRunConfiguration().get()) {
			extension.getRunConfigs().create("datagen", run -> {
				run.inherit(extension.getRunConfigs().getByName("server"));
				run.setConfigName("Data Generation");

				run.property("fabric-api.datagen");
				run.property("fabric-api.datagen.output-dir", outputDirectory.getAbsolutePath());
				run.runDir("build/datagen");

				if (settings.getModId().isPresent()) {
					run.property("fabric-api.datagen.modid", settings.getModId().get());
				}

				if (settings.getStrictValidation().get()) {
					run.property("fabric-api.datagen.strict-validation", "true");
				}

				if (settings.getCreateSourceSet().get()) {
					run.source(DATAGEN_SOURCESET_NAME);
				}
			});
		}
	}

	public interface DataGenerationSettings {
		/**
		 * Contains the output directory where generated data files will be stored.
		 */
		RegularFileProperty getOutputDirectory();

		/**
		 * Contains a boolean indicating whether a run configuration should be created for the data generation process.
		 */
		Property<Boolean> getCreateRunConfiguration();

		/**
		 * Contains a boolean property indicating whether a new source set should be created for the data generation process.
		 */
		Property<Boolean> getCreateSourceSet();

		/**
		 * Contains a string property representing the mod ID associated with the data generation process.
		 *
		 * <p>This must be set when {@link #getCreateRunConfiguration()} is set.
		 */
		Property<String> getModId();

		/**
		 * Contains a boolean property indicating whether strict validation is enabled.
		 */
		Property<Boolean> getStrictValidation();

		/**
		 * Contains a boolean property indicating whether the generated resources will be automatically added to the main sourceset.
		 */
		Property<Boolean> getAddToResources();
	}

	private String getDependencyNotation(String moduleName, String fabricApiVersion) {
		return String.format("net.fabricmc.fabric-api:%s:%s", moduleName, moduleVersion(moduleName, fabricApiVersion));
	}

	private Map<String, String> getApiModuleVersions(String fabricApiVersion) {
		try {
			return populateModuleVersionMap(getApiMavenPom(fabricApiVersion));
		} catch (PomNotFoundException e) {
			throw new RuntimeException("Could not find fabric-api version: " + fabricApiVersion);
		}
	}

	private Map<String, String> getDeprecatedApiModuleVersions(String fabricApiVersion) {
		try {
			return populateModuleVersionMap(getDeprecatedApiMavenPom(fabricApiVersion));
		} catch (PomNotFoundException e) {
			// Not all fabric-api versions have deprecated modules, return an empty map to cache this fact.
			return Collections.emptyMap();
		}
	}

	private Map<String, String> populateModuleVersionMap(File pomFile) {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document pom = docBuilder.parse(pomFile);

			Map<String, String> versionMap = new HashMap<>();

			NodeList dependencies = ((Element) pom.getElementsByTagName("dependencies").item(0)).getElementsByTagName("dependency");

			for (int i = 0; i < dependencies.getLength(); i++) {
				Element dep = (Element) dependencies.item(i);
				Element artifact = (Element) dep.getElementsByTagName("artifactId").item(0);
				Element version = (Element) dep.getElementsByTagName("version").item(0);

				if (artifact == null || version == null) {
					throw new RuntimeException("Failed to find artifact or version");
				}

				versionMap.put(artifact.getTextContent(), version.getTextContent());
			}

			return versionMap;
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse " + pomFile.getName(), e);
		}
	}

	private File getApiMavenPom(String fabricApiVersion) throws PomNotFoundException {
		return getPom("fabric-api", fabricApiVersion);
	}

	private File getDeprecatedApiMavenPom(String fabricApiVersion) throws PomNotFoundException {
		return getPom("fabric-api-deprecated", fabricApiVersion);
	}

	private File getPom(String name, String version) throws PomNotFoundException {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final var mavenPom = new File(extension.getFiles().getUserCache(), "fabric-api/%s-%s.pom".formatted(name, version));

		try {
			extension.download(String.format("https://maven.fabricmc.net/net/fabricmc/fabric-api/%2$s/%1$s/%2$s-%1$s.pom", version, name))
					.defaultCache()
					.downloadPath(mavenPom.toPath());
		} catch (DownloadException e) {
			if (e.getStatusCode() == 404) {
				throw new PomNotFoundException(e);
			}

			throw new UncheckedIOException("Failed to download maven info to " + mavenPom.getName(), e);
		}

		return mavenPom;
	}

	private static class PomNotFoundException extends Exception {
		PomNotFoundException(Throwable cause) {
			super(cause);
		}
	}

	private static void extendsFrom(Project project, String name, String extendsFrom) {
		final ConfigurationContainer configurations = project.getConfigurations();

		configurations.named(name, configuration -> {
			configuration.extendsFrom(configurations.getByName(extendsFrom));
		});
	}
}
