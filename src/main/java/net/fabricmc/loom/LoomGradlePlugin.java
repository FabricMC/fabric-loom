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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loom.data.VersionInfoJson;
import net.fabricmc.loom.data.VersionManifestJson;
import net.fabricmc.loom.tasks.*;
import net.fabricmc.loom.tasks.download.DownloadAction;
import net.fabricmc.loom.tasks.download.DownloadTask;
import net.fabricmc.loom.tasks.fernflower.FernFlowerTask;
import net.fabricmc.loom.tasks.ide.GenIdeaRunConfigsTask;
import net.fabricmc.loom.tasks.sourceremap.SourcesRemapTask;
import net.fabricmc.loom.util.MavenNotation;
import net.fabricmc.loom.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.text.MessageFormat.format;
import static net.fabricmc.loom.util.Constants.*;
import static net.fabricmc.loom.util.Utils.*;

/**
 * Created by covers1624 on 5/02/19.
 */
@SuppressWarnings ("UnstableApiUsage")
public class LoomGradlePlugin implements Plugin<Project> {

    private static final Logger logger = Logging.getLogger("Loom");

    public static final String RESOURCES_URL = "http://resources.download.minecraft.net/";

    public static final List<String> clientDependencyMatches = Arrays.asList(//
            "java3d",//
            "paulscode",//
            "lwjgl",//
            "twitch",//
            "jinput",//
            "text2speech",//
            "objc"//
    );

    //This relies on StringSubstitutor not copying the map. Take note when updating commons-text
    private static final Map<String, String> substMap = new HashMap<>();
    private static final StringSubstitutor substr = new StringSubstitutor(substMap);

    protected LoomGradleExtension extension;

    protected File userCache;
    protected File remappedRepo;

    protected VersionManifestJson.Version minecraftVersion;
    protected VersionInfoJson versionInfo;

    protected TaskProvider<DownloadTask> dlClientJarTask;
    protected TaskProvider<DownloadTask> dlServerJarTask;
    protected TaskProvider<DownloadTask> dlAssetsIndexTask;
    protected TaskProvider<DownloadAssetsTask> dlAssetsTask;
    protected TaskProvider<MergeJarTask> mergeJarsTask;
    protected TaskProvider<ExtractMappingsTask> extractMappingsTask;
    protected TaskProvider<TinyRemapTask> remapMinecraftIntermediaryTask;
    protected TaskProvider<TinyRemapTask> remapMinecraftNamedTask;
    protected TaskProvider<FernFlowerTask> decompileMinecraftNamedTask;
    protected TaskProvider<RemapLineNumbersTask> remapNamedLineNumbersTask;
    protected TaskProvider<Task> remapModCompileTask;
    protected TaskProvider<Task> ideSetupTask;
    protected TaskProvider<GenIdeaRunConfigsTask> genIdeaRuns;

    protected TaskProvider<Task> buildTask;
    protected TaskProvider<Task> assembleTask;
    protected TaskProvider<Jar> jarTask;
    protected TaskProvider<ProcessResources> processResourcesTask;
    protected TaskProvider<SourcesRemapTask> remapSourcesTask;
    protected TaskProvider<Jar> sourcesJarTask;
    protected TaskProvider<TinyRemapTask> remapJarTask;

    @Override
    public void apply(Project project) {
        extension = project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches/fabric-loom");
        remappedRepo = new File(userCache, "/remapped");
        remappedRepo.mkdirs();

        project.afterEvaluate(p -> {
            substMap.put("version", extension.version);
            substMap.put("mappings_artifact", extension.mappings);
        });

        project.afterEvaluate(p -> {
            if (project.getPlugins().hasPlugin("idea")) {
                logger.warn("Using the idea gradle plugin is not recommended and may not work correctly.");
                logger.warn("Please import the gradle project into Intellij. See [TODO] for info.");
            }
        });

        ConfigurationContainer configurations = project.getConfigurations();
        DependencyHandler dependencies = project.getDependencies();
        TaskContainer tasks = project.getTasks();
        RepositoryHandler repositories = project.getRepositories();

        repositories.maven(e -> e.setUrl(remappedRepo.getAbsoluteFile()));
        repositories.mavenLocal();
        repositories.maven(e -> e.setUrl("https://maven.fabricmc.net/"));
        repositories.maven(e -> e.setUrl("https://libraries.minecraft.net/"));
        repositories.jcenter();
        repositories.mavenCentral();

        project.getPlugins().apply("java");

        Configuration compile = configurations.getByName("compile");
        Configuration mcDeps = configurations.maybeCreate(CONFIG_MINECRAFT_DEPS);
        Configuration mcClientDeps = configurations.maybeCreate(CONFIG_MINECRAFT_DEPS_CLIENT);
        Configuration mcAllDeps = configurations.maybeCreate("minecraftAllDependencies");
        Configuration mcNatives = configurations.maybeCreate(CONFIG_MINECRAFT_NATIVES);
        Configuration mcIntermediary = configurations.maybeCreate("minecraftIntermediary");
        Configuration mcNamed = configurations.maybeCreate("minecraftNamed");
        Configuration mcNamedLinemapped = configurations.maybeCreate("minecraftNamedLinemapped");
        Configuration modCompile = configurations.maybeCreate("modCompile");
        Configuration remappedDeps = configurations.maybeCreate("remappedDeps");
        Configuration remappedTransitiveDeps = configurations.maybeCreate("remappedTransitiveDeps").setTransitive(false);

        //Add build script classpath to annotationProcessor configuration. For Mixin's.
        Configuration annotationProcessor = configurations.maybeCreate("annotationProcessor");
        for (File dep : project.getBuildscript().getConfigurations().getByName("classpath")) {
            dependencies.add("annotationProcessor", project.files(dep));
        }

        mcAllDeps.extendsFrom(mcDeps, mcClientDeps);
        compile.extendsFrom(mcAllDeps, mcNatives, remappedDeps, remappedTransitiveDeps);
        annotationProcessor.extendsFrom(compile);

        project.afterEvaluate(p -> {
            dependencies.add("compile", extension.mappings);
        });

        //This is done at configuration time to work around an Intellij Quirk where dependencies added via a
        //task are not added to the project, this is at the cost of slightly greater configuration time, realistically
        //these as separate tasks was redundant as they were _always_ executed.
        //TODO, Re-Evaluate this at some point, perhaps there is a more modular way to do this,
        //       i.e a Local version file, or something of the sort.
        project.afterEvaluate(p -> {
            File manifestFile = new File(userCache, "versions/version_manifest.json");
            DownloadAction dlManifest = new DownloadAction(project);
            dlManifest.setSrc("https://launchermeta.mojang.com/mc/game/version_manifest.json");
            dlManifest.setDest(manifestFile);
            dlManifest.setUseETag(true);
            dlManifest.setOnlyIfModified(true);
            sneaky(dlManifest::execute);

            VersionManifestJson manifest = VersionManifestJson.fromJson(manifestFile);
            minecraftVersion = manifest.findVersion(extension.version)//
                    .orElseThrow(() -> new RuntimeException("Failed to find minecraft version: " + extension.version));

            File versionFile = new File(userCache, format("versions/{0}/{0}.json", extension.version));
            DownloadAction dlVersion = new DownloadAction(project);
            dlVersion.setSrc(minecraftVersion.url);
            dlVersion.setDest(versionFile);
            dlVersion.setUseETag(true);
            dlVersion.setOnlyIfModified(true);
            sneaky(dlVersion::execute);

            versionInfo = VersionInfoJson.fromJson(versionFile);
            versionInfo.libraries.stream()//
                    .filter(VersionInfoJson.Library::allowed)//
                    .forEach(lib -> {
                        if (lib.natives == null) {
                            String dep = lib.getArtifact(false);
                            String config = CONFIG_MINECRAFT_DEPS;
                            for (String str : clientDependencyMatches) {
                                if (dep.contains(str)) {
                                    config = CONFIG_MINECRAFT_DEPS_CLIENT;
                                    break;
                                }
                            }
                            dependencies.add(config, dep);
                        } else {
                            dependencies.add(CONFIG_MINECRAFT_NATIVES, lib.getArtifact(false));
                        }
                    });

        });

        //Run configs rely on asset stuff above.
        project.afterEvaluate(p -> {
            extension.clientRun//
                    .setWorkingDir(project.file(extension.runDir))//
                    .addProgramArg("--assetIndex").addProgramArg(versionInfo.assetIndex.getId(extension.version))//
                    .addProgramArg("--assetsDir").addProgramArg(new File(userCache, "assets").getAbsolutePath());

            extension.serverRun//
                    .setWorkingDir(project.file(extension.runDir));
        });

        dlClientJarTask = tasks.register(TASK_DOWNLOAD_CLIENT_JAR, DownloadTask.class, t -> {
            t.setSrc(laterURL(() -> versionInfo.downloads.get("client").url));
            t.setDest(laterFile(() -> new File(userCache, format("versions/{0}/client.jar", extension.version))));
            t.setUseETag(true);
            t.setOnlyIfModified(true);
        });

        dlServerJarTask = tasks.register(TASK_DOWNLOAD_SERVER_JAR, DownloadTask.class, t -> {
            t.setSrc(laterURL(() -> versionInfo.downloads.get("server").url));
            t.setDest(laterFile(() -> new File(userCache, format("versions/{0}/server.jar", extension.version))));
            t.setUseETag(true);
            t.setOnlyIfModified(true);
        });

        dlAssetsIndexTask = tasks.register(TASK_DOWNLOAD_ASSETS_INDEX, DownloadTask.class, t -> {
            t.setSrc(laterURL(() -> versionInfo.assetIndex.url));
            t.setDest(laterFile(() -> {
                VersionInfoJson.AssetIndex assetIndex = versionInfo.assetIndex;
                return new File(userCache, format("assets/indexes/{0}.json", assetIndex.getId(extension.version)));
            }));
            t.setUseETag(true);
            t.setOnlyIfModified(true);
        });

        dlAssetsTask = tasks.register(TASK_DOWNLOAD_ASSETS, DownloadAssetsTask.class, t -> {
            t.dependsOn(dlAssetsIndexTask);
            t.setAssetsDir(new File(userCache, "assets"));
            t.setAssetIndex(laterTaskOutput(dlAssetsIndexTask));
        });

        mergeJarsTask = tasks.register(TASK_MERGE_JARS, MergeJarTask.class, t -> {
            t.dependsOn(dlClientJarTask, dlServerJarTask);
            t.setClientJar(laterTaskOutput(dlClientJarTask));
            t.setServerJar(laterTaskOutput(dlServerJarTask));
            t.setMergedJar(laterFile(() -> new File(userCache, format("versions/{0}/merged.jar", extension.version))));
            t.setOffsetSyntheticParams(true);
            t.setRemoveSnowmen(true);
        });

        extractMappingsTask = tasks.register(TASK_EXTRACT_MAPPINGS, ExtractMappingsTask.class, t -> {
            t.dependsOn(mergeJarsTask);
            t.setMappingsArtifact(laterString(() -> extension.mappings));
            t.setMergedJar(laterTaskOutput(mergeJarsTask));
            t.setBaseMappings(laterFile(() -> new File(userCache, format("mappings/{0}/base.tiny", extension.mappings.replace(":", "/")))));
            t.setMappings(laterFile(() -> new File(userCache, format("mappings/{0}/mappings.tiny", extension.mappings.replace(":", "/")))));
        });

        MavenNotation intermediaryArtifact = MavenNotation.parse("net.minecraft:minecraft:${version}-intermediary");
        MavenNotation namedArtifact = MavenNotation.parse("net.minecraft:minecraft:${version}-named");
        MavenNotation namedLinemapArtifact = namedArtifact.withClassifier("linemap").withExtension("linemap");
        MavenNotation namedLinemappedArtifact = MavenNotation.parse("net.minecraft:minecraft:${version}-named-linemapped");
        remapMinecraftIntermediaryTask = tasks.register("remapMinecraftIntermediary", TinyRemapTask.class, t -> {
            t.dependsOn(extractMappingsTask, mergeJarsTask);
            t.addMappings(laterTaskOutput(extractMappingsTask));
            t.setFromMappings("official");
            t.setToMappings("intermediary");
            t.setLibraries(mcAllDeps);
            t.setInput(laterTaskOutput(mergeJarsTask));
            t.setOutput(laterFile(() -> remap(intermediaryArtifact).subst(substr).toFile(remappedRepo)));
        });
        project.afterEvaluate(p -> dependencies.add("minecraftIntermediary", remap(intermediaryArtifact).subst(substr).toString()));

        remapMinecraftNamedTask = tasks.register("remapMinecraftNamed", TinyRemapTask.class, t -> {
            t.dependsOn(extractMappingsTask, mergeJarsTask);
            t.addMappings(laterTaskOutput(extractMappingsTask));
            t.setFromMappings("official");
            t.setToMappings("named");
            t.setLibraries(mcAllDeps);
            t.setInput(laterTaskOutput(mergeJarsTask));
            t.setOutput(laterFile(() -> remap(namedArtifact).subst(substr).toFile(remappedRepo)));
        });
        project.afterEvaluate(p -> dependencies.add("minecraftNamed", remap(namedArtifact).subst(substr).toString()));

        decompileMinecraftNamedTask = tasks.register("decompileMinecraftNamed", FernFlowerTask.class, t -> {
            t.dependsOn(remapMinecraftNamedTask);
            t.setInput(laterTaskOutput(remapMinecraftNamedTask));
            t.setOutput(laterFile(() -> remap(namedArtifact).subst(substr).withClassifier("sources").toFile(remappedRepo)));
            t.setLineMapFile(laterFile(() -> remap(namedLinemapArtifact).subst(substr).toFile(remappedRepo)));
            t.setLibraries(mcAllDeps);
            if (!extension.experimentalThreadedFF) {
                t.setNumThreads(0);
            }
            t.doLast(e -> {
                File from = t.getOutput();
                File to = remap(namedLinemappedArtifact).subst(substr).withClassifier("sources").toFile(remappedRepo);
                sneaky(() -> Files.copy(from.toPath(), makeFile(to).toPath(), StandardCopyOption.REPLACE_EXISTING));
            });
        });
        remapNamedLineNumbersTask = tasks.register("remapNamedLineNumbers", RemapLineNumbersTask.class, t -> {
            t.dependsOn(remapMinecraftNamedTask, decompileMinecraftNamedTask);
            t.setInput(laterTaskOutput(remapMinecraftNamedTask));
            t.setOutput(laterFile(() -> remap(namedLinemappedArtifact).subst(substr).toFile(remappedRepo)));
            t.setLineMap(laterFile(() -> remap(namedLinemapArtifact).subst(substr).toFile(remappedRepo)));
        });
        project.afterEvaluate(p -> dependencies.add("minecraftNamedLinemapped", remap(namedLinemappedArtifact).subst(substr).toString()));

        remapModCompileTask = tasks.register("remapModCompile", t -> {
            t.getOutputs().upToDateWhen((e) -> false);
            t.setOnlyIf(e -> true);
            t.doLast(e -> {
            });
        });

        project.afterEvaluate(p -> {
            for (ResolvedArtifactResult artifact : modCompile.getIncoming().getArtifacts().getArtifacts()) {
                //TODO, support anything! Hash the file and use that as its group, filename as name, and 1.0.0 as version. Alternatively parse version and name from artifact.
                if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)) {
                    logger.warn("Found non maven dependency in modCompile. " + artifact.getId().getComponentIdentifier());
                    continue;
                }
                MavenNotation notation = MavenNotation.parse(artifact.getId().getComponentIdentifier().getDisplayName());
                File classes = artifact.getFile();
                AtomicBoolean isFabricMod = new AtomicBoolean(false);
                project.zipTree(classes).visit(f -> {
                    if (f.getName().endsWith("fabric.mod.json")) {
                        logger.info("Found Fabric mod in modCompile: {}", notation.toString());
                        isFabricMod.set(true);
                        f.stopVisiting();
                    }
                });
                if (!isFabricMod.get()) {
                    logger.info("Adding '{}' to remappedTransitiveDeps, does not contain 'fabric.mod.json'", notation.toString());
                    Dependency dep = dependencies.module(notation.toString());
                    if (dep instanceof ModuleDependency) {
                        ((ModuleDependency) dep).setTransitive(false);
                    }
                    dependencies.add("remappedTransitiveDeps", dep);
                    continue;
                }

                AtomicReference<File> sources = new AtomicReference<>();
                @SuppressWarnings ("unchecked")
                ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
                        .forComponents(artifact.getId().getComponentIdentifier())//
                        .withArtifacts(JvmLibrary.class, SourcesArtifact.class);
                outer:
                for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
                    for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
                        if (srcArtifact instanceof ResolvedArtifactResult) {
                            sources.set(((ResolvedArtifactResult) srcArtifact).getFile());
                            break outer;
                        }
                    }
                }
                //TODO, the Libraries of these tasks need to be evaluated, im fairly sure they don't have a complete context at the moment.
                MavenNotation remappedNotation = remap(notation);
                String tskName = notation.toString().replace(":", "");
                TaskProvider<TinyRemapTask> remapClasses = tasks.register("remapDependency_" + tskName, TinyRemapTask.class, t -> {
                    t.dependsOn(extractMappingsTask, remapMinecraftIntermediaryTask);
                    t.addMappings(laterTaskOutput(extractMappingsTask));
                    t.setFromMappings("intermediary");
                    t.setToMappings("named");
                    t.setLibraries(mcAllDeps.plus(mcIntermediary));
                    t.setInput(classes);
                    t.setOutput(remappedNotation.toFile(remappedRepo));
                });
                TaskProvider<SourcesRemapTask> remapSources;
                if (sources.get() != null) {
                    remapSources = tasks.register("remapDependencySources_" + tskName, SourcesRemapTask.class, t -> {
                        t.dependsOn(extractMappingsTask, remapMinecraftIntermediaryTask);
                        t.addMappings(laterTaskOutput(extractMappingsTask));
                        t.setFromMappings("intermediary");
                        t.setToMappings("named");
                        t.setLibraries(mcAllDeps.plus(mcIntermediary));
                        t.setInput(sources.get());
                        t.setOutput(remappedNotation.withClassifier("sources").toFile(remappedRepo));
                    });
                } else {//Messy, but lambda's and effective final.
                    remapSources = null;
                }
                dependencies.add("remappedDeps", remappedNotation.toString());

                remapModCompileTask.configure(t -> {
                    t.dependsOn(remapClasses);
                    if (remapSources != null) {
                        t.dependsOn(remapSources);
                    }
                });
            }
        });

        ideSetupTask = tasks.register("ideSetup", t -> {
            t.setGroup("loom-ide");
            t.dependsOn(decompileMinecraftNamedTask, remapNamedLineNumbersTask, dlAssetsTask, remapModCompileTask);
            compile.extendsFrom(mcNamedLinemapped);
        });

        genIdeaRuns = tasks.register("genIdeaRuns", GenIdeaRunConfigsTask.class, t -> {
            t.setGroup("loom-ide");
            t.dependsOn(ideSetupTask);
            t.setClientRun(extension.clientRun);
            t.setServerRun(extension.serverRun);
        });

        buildTask = tasks.named("build");
        assembleTask = tasks.named("assemble");
        jarTask = tasks.withType(Jar.class).named("jar");

        File mixinMappingsOutput = new File(project.getBuildDir(), "remapJar/mixin.tiny");
        TaskProvider<JavaCompile> compileJavaTask = tasks.withType(JavaCompile.class).named("compileJava");
        compileJavaTask.configure(t -> {
            t.dependsOn(extractMappingsTask, remapMinecraftNamedTask, remapModCompileTask);

            //This is kinda funky, but we assure that extractMappingsTask has been executed before adding args.
            t.getOptions().getCompilerArgumentProviders().add(() -> {
                if (extractMappingsTask.isPresent() && extractMappingsTask.get().getState().getDidWork()) {
                    ExtractMappingsTask mappingsTask = extractMappingsTask.get();
                    List<String> args = new ArrayList<>();
                    args.add("-AinMapFileNamedIntermediary=" + mappingsTask.getMappings().getAbsolutePath());
                    args.add("-AoutMapFileNamedIntermediary=" + mixinMappingsOutput.getAbsolutePath());
                    args.add("-AoutRefMapFile=" + new File(t.getDestinationDir(), extension.getRefmapName()).getAbsolutePath());
                    args.add("-AdefaultObfuscationEnv=named:intermediary");
                    return args;
                }
                return Collections.emptyList();
            });
        });

        processResourcesTask = tasks.withType(ProcessResources.class).named("processResources");
        processResourcesTask.configure(task -> {
            //This is done in doLast so the transformed jsons can be cached.
            task.doLast(e -> {
                String mixinArtifact = null;
                for (ResolvedArtifactResult artifact : compile.getIncoming().getArtifacts().getArtifacts()) {
                    String id = artifact.getId().getComponentIdentifier().getDisplayName();
                    logger.info("Checking if {} is a mixin artifact.", id);
                    if (extension.mixinArtifactRegex.matcher(id).find()) {
                        logger.info("Found mixin artifact: {}", id);
                        mixinArtifact = id;
                        break;
                    }
                }
                String mixinVersion;
                if (mixinArtifact != null) {
                    String[] segs = mixinArtifact.split(":");
                    String vSeg = segs[2];
                    int atIndex = vSeg.indexOf("@");
                    if (atIndex != -1) {
                        vSeg = vSeg.substring(0, atIndex);
                    }
                    if (vSeg.split("\\.").length >= 4) {
                        vSeg = vSeg.substring(0, vSeg.lastIndexOf(".")) + "-SNAPSHOT";
                    }
                    mixinVersion = vSeg;
                } else {
                    mixinVersion = null;
                    logger.warn("Unable to find mixin artifact in dependencies, refmaps will not have minVersion's");
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                FileTree tree = project.fileTree(task.getDestinationDir());
                Set<String> mixins = new HashSet<>();
                tree.visit(f -> {
                    if (f.getName().equals("fabric.mod.json")) {
                        JsonObject modJson = Utils.fromJson(gson, f.getFile(), JsonObject.class);
                        if (modJson.has("mixins")) {
                            JsonObject mixinsObj = modJson.get("mixins").getAsJsonObject();
                            for (Map.Entry<String, JsonElement> entry : mixinsObj.entrySet()) {
                                mixins.add(entry.getValue().getAsString());
                            }
                        }
                        f.stopVisiting();
                    }
                });
                tree.filter(f -> mixins.contains(f.getName())).forEach(mixinFile -> {
                    JsonObject mixinJson = Utils.fromJson(gson, mixinFile, JsonObject.class);
                    if (!mixinJson.has("refmap")) {
                        mixinJson.addProperty("refmap", extension.getRefmapName());
                    }
                    if (!mixinJson.has("minVersion") && mixinVersion != null) {
                        mixinJson.addProperty("minVersion", mixinVersion);
                    }
                    Utils.toJson(gson, mixinJson, JsonObject.class, mixinFile);
                });
            });
        });

        JavaPluginConvention convention = project.getConvention().findPlugin(JavaPluginConvention.class);
        remapSourcesTask = tasks.register("remapSources", SourcesRemapTask.class, t -> {
            File tempInput = new File(t.getTemporaryDir(), "input");
            t.doFirst(e -> project.copy(spec -> {
                spec.from(convention.getSourceSets().getByName("main").getAllSource());
                spec.into(tempInput);
            }));
            t.dependsOn(remapMinecraftNamedTask);
            t.addMappings(laterTaskOutput(extractMappingsTask));
            t.addMappings(mixinMappingsOutput);
            t.setFromMappings("named");
            t.setToMappings("intermediary");
            t.setLibraries(mcAllDeps.plus(mcNamed).plus(remappedDeps));
            t.setInput(tempInput);
            t.setOutput(new File(t.getTemporaryDir(), "output.jar"));
            t.setSimpleCache(true);
            t.doFirst(e -> project.delete(tempInput));
        });

        sourcesJarTask = tasks.register("sourcesJar", Jar.class, t -> {
            project.getArtifacts().add("archives", t);
            t.dependsOn(remapSourcesTask);
            t.setClassifier("sources");
            t.from(project.zipTree(laterTaskOutput(remapSourcesTask)));
            //TODO, we need to pull resources into this.
            //t.from(convention.getSourceSets().getByName("main").getAllSource());
        });
        assembleTask.configure(t -> {
            t.dependsOn(sourcesJarTask);
        });

        //TODO, this task just overwrites the output of the Jar task.
        //TODO,  it should really remove the artifact from the Jar task and add a new one,
        //TODO,  that appears to be easier said than done..
        remapJarTask = tasks.register("remapJar", TinyRemapTask.class, t -> {
            t.dependsOn(extractMappingsTask, remapMinecraftNamedTask);
            t.addMappings(laterTaskOutput(extractMappingsTask));
            t.addMappings(mixinMappingsOutput);
            t.setFromMappings("named");
            t.setToMappings("intermediary");
            t.setLibraries(mcAllDeps.plus(mcNamed).plus(remappedDeps));
            t.setInput(laterTaskOutput(jarTask));
            t.setOutput(laterTaskOutput(jarTask));
            t.setSimpleCache(true);
        });
        jarTask.configure(t -> {
            t.finalizedBy(remapJarTask);
        });

        //temp task, depends on all end points.
        Task tempTask = tasks.create("testTheStuff");
        tempTask.dependsOn(dlAssetsTask, remapMinecraftIntermediaryTask, remapMinecraftNamedTask, remapModCompileTask);
    }

    public MavenNotation remap(MavenNotation notation) {
        String[] mappingSegs = extension.mappings.split(":");
        mappingSegs[2] = mappingSegs[2].replace(".", "-");//Remove '.' from the version.

        String mappings = StringUtils.join(mappingSegs, ".");
        return notation.withGroup(mappings + "." + notation.group);
    }
}
