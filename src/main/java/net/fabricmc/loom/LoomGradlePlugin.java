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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import net.fabricmc.loom.transformers.ContainedZipStrippingTransformer;
import net.fabricmc.loom.transformers.TransformerProjectManager;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.tasks.TaskContainer;

import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftLibraryProvider;
import net.fabricmc.loom.task.AbstractDecompileTask;
import net.fabricmc.loom.task.CleanLoomBinaries;
import net.fabricmc.loom.task.CleanLoomMappings;
import net.fabricmc.loom.task.DownloadAssetsTask;
import net.fabricmc.loom.task.GenEclipseRunsTask;
import net.fabricmc.loom.task.GenIdeaProjectTask;
import net.fabricmc.loom.task.GenVsCodeProjectTask;
import net.fabricmc.loom.task.MigrateMappingsTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapLineNumbersTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.task.RunClientTask;
import net.fabricmc.loom.task.RunServerTask;
import net.fabricmc.loom.task.fernflower.FernFlowerTask;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import static net.fabricmc.loom.util.ModCompileRemapper.findSources;

public class LoomGradlePlugin extends AbstractPlugin {
	private static File getMappedByproduct(Project project, String suffix) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		String path = mappedJar.getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	@Override
	public void apply(Project target) {
		super.apply(target);

		final Attribute<Boolean> stripped = Attribute.of("stripped", Boolean.class);

        TransformerProjectManager.getInstance().register(target);

		target.getDependencies().attributesSchema(schema -> {
		    schema.attribute(stripped);
        });

		target.getDependencies().artifactTypes(types -> {
		    types.all(type -> {
		        target.getLogger().lifecycle("[Loom]: Available: " + type.getName());
            });

		    types.getByName("jar", jarType -> {
		        jarType.getAttributes().attribute(stripped, false);
            });
        });

		target.getDependencies().registerTransform(
          ContainedZipStrippingTransformer.class,
          config -> {
              config.getFrom().attribute(stripped, false);
              config.getTo().attribute(stripped, true);
              config.parameters(parameters -> {
                  parameters.getProjectPathParameter().set(target.getPath());
              });
          }
        );

		TaskContainer tasks = target.getTasks();

		tasks.register("cleanLoomBinaries", CleanLoomBinaries.class);
		tasks.register("cleanLoomMappings", CleanLoomMappings.class);

		tasks.register("cleanLoom").configure(task -> {
			task.dependsOn(tasks.getByName("cleanLoomBinaries"));
			task.dependsOn(tasks.getByName("cleanLoomMappings"));
		});

		tasks.register("migrateMappings", MigrateMappingsTask.class, t -> {
			t.getOutputs().upToDateWhen((o) -> false);
		});

		tasks.register("remapJar", RemapJarTask.class);

		tasks.register("genSourcesDecompile", FernFlowerTask.class, t -> {
			t.getOutputs().upToDateWhen((o) -> false);
		});

		tasks.register("genSourcesRemapLineNumbers", RemapLineNumbersTask.class, t -> {
			t.getOutputs().upToDateWhen((o) -> false);
		});

		tasks.register("genSources", t -> {
			t.getOutputs().upToDateWhen((o) -> false);
			t.setGroup("fabric");
		});

        //final Configuration sourcesConfig = project.getConfigurations().maybeCreate("sources");
        project.getConfigurations().all(config -> {
            if (config.isCanBeResolved())
            {
                config.attributes(container -> {
                    container.attribute(stripped, true);
                });

                /*if (config == sourcesConfig)
                    return;

                //Execute sources linking only for none sources source sets
                config.getDependencies().all(dependency -> {
                    if (dependency instanceof AbstractModuleDependency)
                    {
                        if (findSources(project.getDependencies(), dependency) != null)
                        {
                            final AbstractModuleDependency abstractModuleDependency = (AbstractModuleDependency) dependency;
                            if (abstractModuleDependency.getArtifacts().size() > 0)
                            {
                                abstractModuleDependency.getArtifacts().forEach(dependencyArtifact -> {
                                    String classifierAppendix = "sources";
                                    dependencyArtifact.getClassifier();
                                    if (!dependencyArtifact.getClassifier().isEmpty())
                                    {
                                        classifierAppendix = dependencyArtifact.getClassifier() + "-" + classifierAppendix;
                                    }

                                    final String dependencyId =
                                      String.format("%s:%s:%s:%s", dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifierAppendix);

                                    project.getDependencies().add("sources", dependencyId);

                                    project.getLogger().lifecycle("[SourceAnalysis]: Added: " + dependencyId + " as sources dependency.");
                                });
                            }
                            else
                            {
                                final String dependencyId =
                                  String.format("%s:%s:%s:%s", dependency.getGroup(), dependency.getName(), dependency.getVersion(), "sources");

                                project.getDependencies().add("sources", dependencyId);

                                project.getLogger().lifecycle("[SourceAnalysis]: Added: " + dependencyId + " as sources dependency.");
                            }

                        }
                        else
                        {
                            project.getLogger().lifecycle("[SourceAnalysis]: Could not add " + dependency.toString() + " as there are no sources available.");
                        }
                    }
                });*/
            }
        });

		project.afterEvaluate((p) -> {
    		AbstractDecompileTask decompileTask = (AbstractDecompileTask) p.getTasks().getByName("genSourcesDecompile");
			RemapLineNumbersTask remapLineNumbersTask = (RemapLineNumbersTask) p.getTasks().getByName("genSourcesRemapLineNumbers");
			Task genSourcesTask = p.getTasks().getByName("genSources");

			genSourcesTask.dependsOn(remapLineNumbersTask);
			remapLineNumbersTask.dependsOn(decompileTask);

			Project project = this.getProject();
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
			MinecraftLibraryProvider libraryProvider = extension.getMinecraftProvider().libraryProvider;
			MappingsProvider mappingsProvider = extension.getMappingsProvider();
			File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
			File linemappedJar = getMappedByproduct(project, "-linemapped.jar");
			File sourcesJar = getMappedByproduct(project, "-sources.jar");
			File linemapFile = getMappedByproduct(project, "-sources.lmap");

			decompileTask.setInput(mappedJar);
			decompileTask.setOutput(sourcesJar);
			decompileTask.setLineMapFile(linemapFile);
			decompileTask.setLibraries(libraryProvider.getLibraries());

			remapLineNumbersTask.setInput(mappedJar);
			remapLineNumbersTask.setLineMapFile(linemapFile);
			remapLineNumbersTask.setOutput(linemappedJar);

			Path mappedJarPath = mappedJar.toPath();
			Path linemappedJarPath = linemappedJar.toPath();

			genSourcesTask.doLast((tt) -> {
				if (Files.exists(linemappedJarPath)) {
					try {
						Files.deleteIfExists(mappedJarPath);
						Files.copy(linemappedJarPath, mappedJarPath);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
		});

		tasks.register("downloadAssets", DownloadAssetsTask.class);

		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.dependsOn("idea", "downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("remapSourcesJar", RemapSourcesJarTask.class);

		tasks.register("runClient", RunClientTask.class, t -> {
			t.dependsOn("assemble", "downloadAssets");
			t.setGroup("minecraftMapped");
		});

		tasks.register("runServer", RunServerTask.class, t -> {
			t.dependsOn("assemble");
			t.setGroup("minecraftMapped");
		});
	}
}
