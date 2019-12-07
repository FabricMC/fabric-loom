package net.fabricmc.loom.ide.idea;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import groovy.lang.Closure;
import groovy.lang.MetaProperty;
import groovy.lang.Reference;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.plugins.ide.internal.tooling.TasksFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalFilter;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProjectPreview;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

public class ExternalModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return ExternalProject.class.getName().equals(modelName) || ExternalProjectPreview.class.getName().equals(modelName);
    }

    @Nullable
    @Override
    public Object buildAll(@NotNull final String modelName, @NotNull final Project project) {
        if (!project.equals(project.getRootProject())) return null;

        Map<Project, ExternalProject> cache = context.getData(PROJECTS_PROVIDER);
        Object tasksFactory = context.getData(getProperty("TASKS_PROVIDER"));
        SourceSetCachedFinder sourceSetFinder = new SourceSetCachedFinder(context);
        return doBuild(modelName, project, cache, tasksFactory, sourceSetFinder);
    }

    @Nullable
    private static Object doBuild(
            final String modelName,
            final Project project,
            Map<Project, ExternalProject> cache,
            TasksFactory tasksFactory,
            SourceSetCachedFinder sourceSetFinder) {
        ExternalProject externalProject = cache.get(project);
        if (externalProject != null) return externalProject;

        boolean resolveSourceSetDependencies = DefaultGroovyMethods.asType(System.getProperties().idea.resolveSourceSetDependencies, (Class<Object>) Boolean.class);
        Boolean isPreview = ExternalProjectPreview.class.getName().equals(modelName);
        DefaultExternalProject defaultExternalProject = new DefaultExternalProject();
        defaultExternalProject.externalSystemId = "GRADLE";
        defaultExternalProject.name = project.getName();
        String qName = ":".equals(project.getPath()) ? project.getName() : project.getPath();
        defaultExternalProject.QName = qName;
        final IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class);
        final IdeaModel model = (ideaPlugin == null ? null : ideaPlugin.getModel());
        IdeaModule ideaPluginModule = (model == null ? null : model.getModule());
        final Gradle parent = project.getGradle().getParent();
        Project parentBuildRootProject = (parent == null ? null : parent.getRootProject());
        final IdeaModel model1 = (ideaPlugin == null ? null : ideaPlugin.getModel());
        final IdeaProject project = (model1 == null ? null : model1.getProject());
        final String name = (project == null ? null : ((Project) project).getName());
        String compositePrefix = parentBuildRootProject && !DefaultGroovyMethods.is(project.getRootProject(), parentBuildRootProject) && !":".equals(project.getPath()) ? (
                StringGroovyMethods.asBoolean(name)
                        ? name
                        : project.getRootProject().getName()) : "";
        final String name1 = (ideaPluginModule == null ? null : ideaPluginModule.getName());
        String ideaModuleName = StringGroovyMethods.asBoolean(name1) ? name1 : project.getName();
        defaultExternalProject.id = compositePrefix + (":".equals(project.getPath()) ? ideaModuleName : qName);
        defaultExternalProject.version = wrap(project.getVersion());
        defaultExternalProject.description = project.getDescription();
        defaultExternalProject.buildDir = project.getBuildDir();
        defaultExternalProject.buildFile = project.getBuildFile();
        defaultExternalProject.group = wrap(project.getGroup());
        defaultExternalProject.projectDir = project.getProjectDir();
        defaultExternalProject.sourceSets = getSourceSets(project, isPreview, resolveSourceSetDependencies, sourceSetFinder);
        defaultExternalProject.tasks = getTasks(project, tasksFactory);

        addArtifactsData(project, defaultExternalProject);

        final Map<String, DefaultExternalProject> childProjects = new TreeMap<String, DefaultExternalProject>();
        for (Map.Entry<String, Project> projectEntry : project.getChildProjects().entrySet()) {
            final Object externalProjectChild = doBuild(modelName, projectEntry.getValue(), cache, tasksFactory, sourceSetFinder);
            if (externalProjectChild instanceof DefaultExternalProject) {
                childProjects.put.call(projectEntry.getKey(), (DefaultExternalProject) externalProjectChild);
            }
            else if (externalProjectChild instanceof ExternalProject) {
                // convert from proxy to our model class
                childProjects.put.call(projectEntry.getKey(), new DefaultExternalProject((ExternalProject) externalProjectChild));
            }
        }

        defaultExternalProject.invokeMethod("setChildProjects", new Object[] {childProjects});
        cache.put.call(project, defaultExternalProject);

        return defaultExternalProject;
    }

    public static void addArtifactsData(final Project project, DefaultExternalProject externalProject) {
        final List<File> artifacts = new ArrayList<File>();
        for (Jar jar : project.getTasks().withType(Jar.class)) {
            try {
                // TODO use getArchiveFile method since Gradle 5.1
                artifacts.add(jar.getArchivePath());
            }
            catch (Exception e) {
                // TODO add reporting for such issues
                project.getLogger().error("warning: [task " + jar.getPath() + "] " + e.getMessage());
            }
        }

        externalProject.invokeMethod("setArtifacts", new Object[] {artifacts});

        SortedMap<String, Configuration> configurationsByName = project.getConfigurations().getAsMap();
        Map<String, Set<File>> artifactsByConfiguration = new HashMap<String, Set<File>>();
        for (Map.Entry<String, Configuration> configurationEntry : configurationsByName.entrySet()) {
            Set<File> files = configurationEntry.getValue().getArtifacts().getFiles().getFiles();
            artifactsByConfiguration.put(configurationEntry.getKey(), new LinkedHashSet<File>(files));
        }

        externalProject.invokeMethod("setArtifactsByConfiguration", new Object[] {artifactsByConfiguration});
    }

    public static Map<String, DefaultExternalTask> getTasks(Project project, TasksFactory tasksFactory) {
        Map<String, DefaultExternalTask> result = new Map<String, DefaultExternalTask>() {
        };

        for (Task task : tasksFactory.invokeMethod("getTasks", new Object[] {project})) {
            DefaultExternalTask externalTask = result.get(task.getName());
            if (externalTask == null) {
                externalTask = new DefaultExternalTask();
                externalTask.name = task.getName();
                externalTask.QName = task.getName();
                externalTask.description = task.getDescription();
                final String group = task.getGroup();
                externalTask.group = StringGroovyMethods.asBoolean(group) ? group : "other";
                final ExtensionContainer extensions = task.getExtensions();
                ExtraPropertiesExtension ext = (extensions == null ? null : extensions.getExtraProperties());
                externalTask.test = (task instanceof Test) || (ext.has("idea.internal.test") && Boolean.valueOf(ext.get("idea.internal.test").toString()));
                externalTask.type = ProjectExtensionsDataBuilderImpl.invokeMethod("getType", new Object[] {task});
                result.put(externalTask.name, externalTask);
            }


            String projectTaskPath = (project.getPath().equals(":") ? ":" : project.getPath() + ":") + task.getName();
            if (projectTaskPath.equals(task.getPath())) {
                externalTask.QName = task.getPath();
            }
        }

        return ((Map<String, DefaultExternalTask>) (result));
    }

    private static Map<String, DefaultExternalSourceSet> getSourceSets(
            final Project project,
            final boolean isPreview,
            final boolean resolveSourceSetDependencies,
            final SourceSetCachedFinder sourceSetFinder) {
        final IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class);
        final IdeaModel model = (ideaPlugin == null ? null : ideaPlugin.getModel());
        final IdeaModule ideaPluginModule = (model == null ? null : model.getModule());
        final Boolean dirs = (ideaPluginModule == null ? null : ideaPluginModule.getInheritOutputDirs());
        final boolean inheritOutputDirs = dirs ? dirs : false;
        final File ideaPluginOutDir = (ideaPluginModule == null ? null : ideaPluginModule.getOutputDir());
        final File ideaPluginTestOutDir = (ideaPluginModule == null ? null : ideaPluginModule.getTestOutputDir());
        final Reference<Object> generatedSourceDirs = new Reference<Object>(null);
        final Reference<Object> ideaSourceDirs = new Reference<Object>(null);
        final Reference<Object> ideaResourceDirs = new Reference<Object>(null);
        final Reference<Object> ideaTestSourceDirs = new Reference<Object>(null);
        final Reference<Object> ideaTestResourceDirs = new Reference<Object>(null);
        final Reference<Boolean> downloadJavadoc = new Reference<Boolean>(false);
        final Reference<Boolean> downloadSources = new Reference<Boolean>(true);

        if (DefaultGroovyMethods.asBoolean(ideaPluginModule)) {
            generatedSourceDirs.set(DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(ideaPluginModule, "generatedSourceDirs")) ? new LinkedHashSet<File>(
                    ideaPluginModule.getGeneratedSourceDirs()) : null);
            ideaSourceDirs.set(new LinkedHashSet<File>(ideaPluginModule.getSourceDirs()));
            ideaResourceDirs.set(DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(ideaPluginModule, "resourceDirs"))
                    ? new LinkedHashSet<File>(ideaPluginModule.getResourceDirs())
                    : new ArrayList());
            ideaTestSourceDirs.set(new LinkedHashSet<File>(ideaPluginModule.getTestSourceDirs()));
            ideaTestResourceDirs.set(DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(ideaPluginModule, "testResourceDirs")) ? new LinkedHashSet<File>(
                    ideaPluginModule.getTestResourceDirs()) : new ArrayList());
            downloadJavadoc.set(ideaPluginModule.isDownloadJavadoc());
            downloadSources.set(ideaPluginModule.isDownloadSources());
        }


        final Reference<Object> projectSourceCompatibility;
        final Reference<Object> projectTargetCompatibility;

        Object javaPluginConvention = JavaPluginUtil.invokeMethod("getJavaPluginConvention", new Object[] {project});
        if (javaPluginConvention != null) {
            projectSourceCompatibility.set(javaPluginConvention.sourceCompatibility.toString());
            projectTargetCompatibility.set(javaPluginConvention.targetCompatibility.toString());
        }


        final Map<String, DefaultExternalSourceSet> result = new Map<String, DefaultExternalSourceSet>() {
        };
        Object sourceSets = JavaPluginUtil.invokeMethod("getSourceSetContainer", new Object[] {project});
        if (sourceSets == null) {
            return ((Map<String, DefaultExternalSourceSet>) (result));
        }


        // ignore inherited source sets from parent project
        Object parentProjectSourceSets = project.getParent() == null ? null : JavaPluginUtil.invokeMethod("getSourceSetContainer", new Object[] {project.getParent()});
        if (parentProjectSourceSets && DefaultGroovyMethods.is(sourceSets, parentProjectSourceSets)) {
            return ((Map<String, DefaultExternalSourceSet>) (result));
        }


        final Iterator<List> iterator = getFilters(project, "processResources").iterator();
        Object resourcesIncludes = iterator.hasNext() ? iterator.next() : null;
        Object resourcesExcludes = iterator.hasNext() ? iterator.next() : null;
        Object filterReaders = iterator.hasNext() ? iterator.next() : null;

        final Iterator<List> iterator1 = getFilters(project, "processTestResources").iterator();
        Object testResourcesIncludes = iterator1.hasNext() ? iterator1.next() : null;
        Object testResourcesExcludes = iterator1.hasNext() ? iterator1.next() : null;
        Object testFilterReaders = iterator1.hasNext() ? iterator1.next() : null;

        //def (javaIncludes,javaExcludes) = getFilters(project,'compileJava')

        final Collection<File> additionalIdeaGenDirs = DefaultGroovyMethods.asType(new ArrayList(), Collection.class);
        if (generatedSourceDirs.get() && !((LinkedHashSet<File>) generatedSourceDirs.get()).isEmpty()) {
            additionalIdeaGenDirs.addAll(generatedSourceDirs.get());
        }

        sourceSets.invokeMethod("all", new Object[] {new Closure<ExternalSourceSet>(this, this) {
            public ExternalSourceSet doCall(SourceSet sourceSet) {
                ExternalSourceSet externalSourceSet = (ExternalSourceSet) new DefaultExternalSourceSet();
                externalSourceSet.name = sourceSet.getName();

                Task javaCompileTask = project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
                if (javaCompileTask instanceof JavaCompile) {
                    final String compatibility = ((JavaCompile) javaCompileTask).getSourceCompatibility();
                    externalSourceSet.sourceCompatibility = StringGroovyMethods.asBoolean(compatibility) ? compatibility : projectSourceCompatibility.get();
                    final String compatibility1 = ((JavaCompile) javaCompileTask).getTargetCompatibility();
                    externalSourceSet.targetCompatibility = StringGroovyMethods.asBoolean(compatibility1) ? compatibility1 : projectTargetCompatibility.get();
                }
                else {
                    externalSourceSet.sourceCompatibility = projectSourceCompatibility.get();
                    externalSourceSet.targetCompatibility = projectTargetCompatibility.get();
                }


                Task jarTask = project.getTasks().findByName(sourceSet.getJarTaskName());
                if (jarTask instanceof AbstractArchiveTask) {
                    externalSourceSet.artifacts = new ArrayList<File>(Arrays.asList(((AbstractArchiveTask) jarTask).getArchivePath()));
                }


                Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources = new Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet>() {
                };
                ExternalSourceDirectorySet resourcesDirectorySet = (ExternalSourceDirectorySet) new DefaultExternalSourceDirectorySet();
                resourcesDirectorySet.name = sourceSet.getResources().getName();
                resourcesDirectorySet.srcDirs = sourceSet.getResources().getSrcDirs();
                if (ideaPluginOutDir && SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    DefaultGroovyMethods.invokeMethod(resourcesDirectorySet, "addGradleOutputDir", new Object[] {ideaPluginOutDir});
                }

                if (ideaPluginTestOutDir && SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    DefaultGroovyMethods.invokeMethod(resourcesDirectorySet, "addGradleOutputDir", new Object[] {ideaPluginTestOutDir});
                }

                if (is4OrBetter) {
                    if (DefaultGroovyMethods.asBoolean(sourceSet.getOutput().getResourcesDir())) {
                        DefaultGroovyMethods.invokeMethod(resourcesDirectorySet, "addGradleOutputDir", new Object[] {sourceSet.getOutput().getResourcesDir()});
                    }
                    else {
                        for (File outDir : sourceSet.getOutput().getClassesDirs().getFiles()) {
                            DefaultGroovyMethods.invokeMethod(resourcesDirectorySet, "addGradleOutputDir", new Object[] {outDir});
                        }

                        if (resourcesDirectorySet.getGradleOutputDirs().isEmpty()) {
                            DefaultGroovyMethods.invokeMethod(resourcesDirectorySet, "addGradleOutputDir", new Object[] {project.getBuildDir()});
                        }
                    }
                }
                else {
                    DefaultGroovyMethods.invokeMethod(resourcesDirectorySet,
                            "addGradleOutputDir",
                            new Object[] {ExternalModelBuilder.chooseNotNull(sourceSet.getOutput().getResourcesDir(),
                                    sourceSet.getOutput().classesDir,
                                    project.getBuildDir())});
                }


                File ideaOutDir = new File(project.getProjectDir(),
                        "out/" + (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) || (!resolveSourceSetDependencies && !SourceSet.TEST_SOURCE_SET_NAME.equals(
                                sourceSet.getName())) ? "production" : GUtil.toLowerCamelCase(sourceSet.getName())));
                resourcesDirectorySet.outputDir = new File(ideaOutDir, "resources");
                resourcesDirectorySet.inheritedCompilerOutput = inheritOutputDirs;

                ExternalSourceDirectorySet javaDirectorySet = (ExternalSourceDirectorySet) new DefaultExternalSourceDirectorySet();
                javaDirectorySet.name = sourceSet.getAllJava().getName();
                javaDirectorySet.srcDirs = sourceSet.getAllJava().getSrcDirs();
                if (ideaPluginOutDir && SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    DefaultGroovyMethods.invokeMethod(javaDirectorySet, "addGradleOutputDir", new Object[] {ideaPluginOutDir});
                }

                if (ideaPluginTestOutDir && SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    DefaultGroovyMethods.invokeMethod(javaDirectorySet, "addGradleOutputDir", new Object[] {ideaPluginTestOutDir});
                }

                if (is4OrBetter) {
                    for (File outDir : sourceSet.getOutput().getClassesDirs().getFiles()) {
                        DefaultGroovyMethods.invokeMethod(javaDirectorySet, "addGradleOutputDir", new Object[] {outDir});
                    }

                    if (javaDirectorySet.getGradleOutputDirs().isEmpty()) {
                        DefaultGroovyMethods.invokeMethod(javaDirectorySet, "addGradleOutputDir", new Object[] {project.getBuildDir()});
                    }
                }
                else {
                    DefaultGroovyMethods.invokeMethod(javaDirectorySet,
                            "addGradleOutputDir",
                            new Object[] {ExternalModelBuilder.chooseNotNull(sourceSet.getOutput().classesDir, project.getBuildDir())});
                }


                javaDirectorySet.outputDir = new File(ideaOutDir, "classes");
                javaDirectorySet.inheritedCompilerOutput = inheritOutputDirs;
//      javaDirectorySet.excludes = javaExcludes + sourceSet.java.excludes;
//      javaDirectorySet.includes = javaIncludes + sourceSet.java.includes;

                DefaultExternalSourceDirectorySet generatedDirectorySet = null;
                Boolean hasExplicitlyDefinedGeneratedSources =
                        generatedSourceDirs.get() && !DefaultGroovyMethods.asBoolean(generatedSourceDirs.get().invokeMethod("isEmpty", new Object[0]));
                if (hasExplicitlyDefinedGeneratedSources) {

                    HashSet<File> files = new HashSet<File>();
                    for (File file : generatedSourceDirs.get()) {
                        if (javaDirectorySet.getSrcDirs().contains(file)) {
                            files.add(file);
                        }
                    }


                    if (!files.isEmpty()) {
                        javaDirectorySet.getSrcDirs().removeAll(files);
                        generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                        generatedDirectorySet.name = "generated " + javaDirectorySet.getName();
                        generatedDirectorySet.srcDirs = files;
                        for (File file : javaDirectorySet.getGradleOutputDirs()) {
                            generatedDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {file});
                        }

                        generatedDirectorySet.outputDir = javaDirectorySet.getOutputDir();
                        generatedDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited();
                    }

                    additionalIdeaGenDirs.removeAll(files);
                }


                if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    if (!inheritOutputDirs && ideaPluginTestOutDir != null) {
                        javaDirectorySet.outputDir = ideaPluginTestOutDir;
                        resourcesDirectorySet.outputDir = ideaPluginTestOutDir;
                    }

                    resourcesDirectorySet.excludes = testResourcesExcludes + sourceSet.getResources().getExcludes();
                    resourcesDirectorySet.includes = testResourcesIncludes + sourceSet.getResources().getIncludes();
                    resourcesDirectorySet.filters = testFilterReaders;
                    sources.put(ExternalSystemSourceType.TEST, javaDirectorySet);
                    sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
                    if (generatedDirectorySet.asBoolean()) {
                        sources.put.call(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet);
                    }
                }
                else {
                    boolean isTestSourceSet = false;
                    if (!inheritOutputDirs && resolveSourceSetDependencies && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) && ideaTestSourceDirs.get()
                            && ((Collection) ideaTestSourceDirs.get()).containsAll(javaDirectorySet.getSrcDirs())) {
                        javaDirectorySet.outputDir =
                                DefaultGroovyMethods.asBoolean(ideaPluginTestOutDir) ? ideaPluginTestOutDir : new File(project.getProjectDir(), "out/test/classes");
                        resourcesDirectorySet.outputDir =
                                DefaultGroovyMethods.asBoolean(ideaPluginTestOutDir) ? ideaPluginTestOutDir : new File(project.getProjectDir(), "out/test/resources");
                        sources.put(ExternalSystemSourceType.TEST, javaDirectorySet);
                        sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
                        isTestSourceSet = true;
                    }
                    else if (!inheritOutputDirs && ideaPluginOutDir != null) {
                        javaDirectorySet.outputDir = ideaPluginOutDir;
                        resourcesDirectorySet.outputDir = ideaPluginOutDir;
                    }


                    resourcesDirectorySet.excludes = resourcesExcludes + sourceSet.getResources().getExcludes();
                    resourcesDirectorySet.includes = resourcesIncludes + sourceSet.getResources().getIncludes();
                    resourcesDirectorySet.filters = filterReaders;
                    if (!isTestSourceSet) {
                        sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet);
                        sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet);
                    }


                    if (!resolveSourceSetDependencies && ideaTestSourceDirs.get()) {
                        Collection<File> testDirs = DefaultGroovyMethods.intersect(javaDirectorySet.getSrcDirs(), (Collection) ideaTestSourceDirs.get());
                        if (!testDirs.isEmpty()) {
                            javaDirectorySet.getSrcDirs().removeAll(ideaTestSourceDirs.get());

                            DefaultExternalSourceDirectorySet testDirectorySet = new DefaultExternalSourceDirectorySet();
                            testDirectorySet.name = javaDirectorySet.getName();
                            testDirectorySet.srcDirs = testDirs;
                            testDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {javaDirectorySet.getOutputDir()});
                            testDirectorySet.outputDir =
                                    DefaultGroovyMethods.asBoolean(ideaPluginTestOutDir) ? ideaPluginTestOutDir : new File(project.getProjectDir(), "out/test/classes");
                            testDirectorySet.inheritedCompilerOutput = javaDirectorySet.isCompilerOutputPathInherited();
                            sources.put.call(ExternalSystemSourceType.TEST, testDirectorySet);
                        }


                        Collection<File> testResourcesDirs = DefaultGroovyMethods.intersect(resourcesDirectorySet.getSrcDirs(), (Collection) ideaTestSourceDirs.get());
                        if (!testResourcesDirs.isEmpty()) {
                            resourcesDirectorySet.getSrcDirs().removeAll(ideaTestSourceDirs.get());

                            DefaultExternalSourceDirectorySet testResourcesDirectorySet = new DefaultExternalSourceDirectorySet();
                            testResourcesDirectorySet.name = resourcesDirectorySet.getName();
                            testResourcesDirectorySet.srcDirs = testResourcesDirs;
                            testResourcesDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {resourcesDirectorySet.getOutputDir()});
                            testResourcesDirectorySet.outputDir =
                                    DefaultGroovyMethods.asBoolean(ideaPluginTestOutDir) ? ideaPluginTestOutDir : new File(project.getProjectDir(), "out/test/resources");
                            testResourcesDirectorySet.inheritedCompilerOutput = resourcesDirectorySet.isCompilerOutputPathInherited();
                            sources.put.call(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet);
                        }
                    }


                    if (generatedDirectorySet.asBoolean()) {
                        sources.put.call(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet);
                        if (!resolveSourceSetDependencies && ideaTestSourceDirs.get()) {
                            Object testGeneratedDirs = generatedDirectorySet.srcDirs.invokeMethod("intersect", new Object[] {(Collection) ideaTestSourceDirs.get()});
                            if (!DefaultGroovyMethods.asBoolean(testGeneratedDirs.invokeMethod("isEmpty", new Object[0]))) {
                                generatedDirectorySet.srcDirs.invokeMethod("removeAll", new Object[] {ideaTestSourceDirs.get()});

                                DefaultExternalSourceDirectorySet testGeneratedDirectorySet = new DefaultExternalSourceDirectorySet();
                                testGeneratedDirectorySet.name = generatedDirectorySet.name;
                                testGeneratedDirectorySet.srcDirs = testGeneratedDirs;
                                testGeneratedDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {generatedDirectorySet.outputDir});
                                testGeneratedDirectorySet.outputDir = generatedDirectorySet.outputDir;
                                testGeneratedDirectorySet.inheritedCompilerOutput = generatedDirectorySet.invokeMethod("isCompilerOutputPathInherited", new Object[0]);

                                sources.put.call(ExternalSystemSourceType.TEST_GENERATED, testGeneratedDirectorySet);
                            }
                        }
                    }


                    if (ideaPluginModule && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) && !SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                        for (DefaultExternalSourceDirectorySet sourceDirectorySet : sources.values()) {
                            ideaSourceDirs.get().invokeMethod("removeAll", new Object[] {sourceDirectorySet.srcDirs});
                            ideaResourceDirs.get().invokeMethod("removeAll", new Object[] {sourceDirectorySet.srcDirs});
                            ideaTestSourceDirs.get().invokeMethod("removeAll", new Object[] {sourceDirectorySet.srcDirs});
                            ideaTestResourceDirs.get().invokeMethod("removeAll", new Object[] {sourceDirectorySet.srcDirs});
                        }
                    }
                }


                if (resolveSourceSetDependencies) {
                    Object dependencies = new DependencyResolverImpl(project,
                            isPreview,
                            downloadJavadoc.get(),
                            downloadSources.get(),
                            sourceSetFinder).invokeMethod("resolveDependencies", new Object[] {sourceSet});
                    DefaultGroovyMethods.invokeMethod(externalSourceSet.getDependencies(), "addAll", new Object[] {dependencies});
                }


                externalSourceSet.sources = sources;
                result.put(sourceSet.getName(), externalSourceSet);
                return externalSourceSet;
            }
        }});

        DefaultExternalSourceSet mainSourceSet = result.get(SourceSet.MAIN_SOURCE_SET_NAME);
        if (ideaPluginModule && mainSourceSet && ideaSourceDirs.get() && !((LinkedHashSet<File>) ideaSourceDirs.get()).isEmpty()) {
            Object mainGradleSourceSet = sourceSets.invokeMethod("findByName", new Object[] {SourceSet.MAIN_SOURCE_SET_NAME});
            if (mainGradleSourceSet.asBoolean()) {
                Object mainSourceDirectorySet = mainSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.SOURCE});
                if (mainSourceDirectorySet.asBoolean()) {
                    mainSourceDirectorySet.srcDirs.invokeMethod("addAll",
                            new Object[] {ideaSourceDirs.get() - (mainGradleSourceSet.resources.srcDirs + generatedSourceDirs.get())});
                }

                Object mainResourceDirectorySet = mainSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.RESOURCE});
                if (mainResourceDirectorySet.asBoolean()) {
                    mainResourceDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {ideaResourceDirs.get()});
                }


                if (!additionalIdeaGenDirs.isEmpty()) {
                    Collection<File> mainAdditionalGenDirs = DefaultGroovyMethods.intersect(additionalIdeaGenDirs, ideaSourceDirs.get());
                    Object mainGenSourceDirectorySet = mainSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.SOURCE_GENERATED});
                    if (mainGenSourceDirectorySet.asBoolean()) {
                        mainGenSourceDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {mainAdditionalGenDirs});
                    }
                    else {
                        DefaultExternalSourceDirectorySet generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                        generatedDirectorySet.name = "generated " + mainSourceSet.name;
                        generatedDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {mainAdditionalGenDirs});
                        generatedDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {mainSourceDirectorySet.outputDir});
                        generatedDirectorySet.outputDir = mainSourceDirectorySet.outputDir;
                        generatedDirectorySet.inheritedCompilerOutput = mainSourceDirectorySet.invokeMethod("isCompilerOutputPathInherited", new Object[0]);
                        mainSourceSet.sources.invokeMethod("put", new Object[] {ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet});
                    }
                }
            }
        }


        DefaultExternalSourceSet testSourceSet = result.get(SourceSet.TEST_SOURCE_SET_NAME);
        if (ideaPluginModule && testSourceSet && ideaTestSourceDirs.get() && !((LinkedHashSet<File>) ideaTestSourceDirs.get()).isEmpty()) {
            Object testGradleSourceSet = sourceSets.invokeMethod("findByName", new Object[] {SourceSet.TEST_SOURCE_SET_NAME});
            if (testGradleSourceSet.asBoolean()) {
                Object testSourceDirectorySet = testSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.TEST});
                if (testSourceDirectorySet.asBoolean()) {
                    testSourceDirectorySet.srcDirs.invokeMethod("addAll",
                            new Object[] {ideaTestSourceDirs.get() - (testGradleSourceSet.resources.srcDirs + generatedSourceDirs.get())});
                }

                Object testResourceDirectorySet = testSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.TEST_RESOURCE});
                if (testResourceDirectorySet.asBoolean()) {
                    testResourceDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {ideaTestResourceDirs.get()});
                }


                if (!additionalIdeaGenDirs.isEmpty()) {
                    Collection<File> testAdditionalGenDirs = DefaultGroovyMethods.intersect(additionalIdeaGenDirs, ideaTestSourceDirs.get());
                    Object testGenSourceDirectorySet = testSourceSet.sources.invokeMethod("get", new Object[] {ExternalSystemSourceType.TEST_GENERATED});
                    if (testGenSourceDirectorySet.asBoolean()) {
                        testGenSourceDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {testAdditionalGenDirs});
                    }
                    else {
                        DefaultExternalSourceDirectorySet generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                        generatedDirectorySet.name = "generated " + testSourceSet.name;
                        generatedDirectorySet.srcDirs.invokeMethod("addAll", new Object[] {testAdditionalGenDirs});
                        generatedDirectorySet.invokeMethod("addGradleOutputDir", new Object[] {testSourceDirectorySet.outputDir});
                        generatedDirectorySet.outputDir = testSourceDirectorySet.outputDir;
                        generatedDirectorySet.inheritedCompilerOutput = testSourceDirectorySet.invokeMethod("isCompilerOutputPathInherited", new Object[0]);
                        testSourceSet.sources.invokeMethod("put", new Object[] {ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet});
                    }
                }
            }
        }


        cleanupSharedSourceFolders(result);

        return ((Map<String, DefaultExternalSourceSet>) (result));
    }

    private static boolean isEmpty(FileCollection collection) {
        try {
            return collection.isEmpty();
        }
        catch (Throwable ignored) {
        }

        return true;
    }

    private static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> map) {
        ExternalSourceSet mainSourceSet = map.get(SourceSet.MAIN_SOURCE_SET_NAME);
        cleanupSharedSourceFolders(map, mainSourceSet, null);
        cleanupSharedSourceFolders(map, map.get(SourceSet.TEST_SOURCE_SET_NAME), mainSourceSet);
    }

    private static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> result, ExternalSourceSet sourceSet, ExternalSourceSet toIgnore) {
        if (!DefaultGroovyMethods.asBoolean(sourceSet)) return;


        for (Map.Entry<String, ExternalSourceSet> sourceSetEntry : result.entrySet()) {
            if (!DefaultGroovyMethods.is(sourceSetEntry.getValue(), sourceSet) && !DefaultGroovyMethods.is(sourceSetEntry.getValue(), toIgnore)) {
                ExternalSourceSet customSourceSet = sourceSetEntry.getValue();
                for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
                    ExternalSourceDirectorySet customSourceDirectorySet =
                            DefaultGroovyMethods.asType(customSourceSet.getSources().get(sourceType), ExternalSourceDirectorySet.class);
                    if (DefaultGroovyMethods.asBoolean(customSourceDirectorySet)) {
                        for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sourceDirEntry : sourceSet.getSources().entrySet()) {
                            customSourceDirectorySet.getSrcDirs().removeAll(sourceDirEntry.getValue().getSrcDirs());
                        }
                    }
                }
            }
        }
    }

    public static <T> T chooseNotNull(T... params) {
        //noinspection GrUnresolvedAccess
        return ((T) (DefaultGroovyMethods.findResult(params, "", new Closure<T>(null, null) {
            public T doCall(T it) {return it;}

            public T doCall() {
                return doCall(null);
            }
        })));
    }

    public static List<List> getFilters(Project project, String taskName) {
        List<Object> includes = new ArrayList();
        List<Object> excludes = new ArrayList();
        final List<ExternalFilter> filterReaders = DefaultGroovyMethods.asType(new ArrayList(), List.class);
        Task filterableTask = project.getTasks().findByName(taskName);
        if (filterableTask instanceof PatternFilterable) {
            includes = DefaultGroovyMethods.plus(includes, ((PatternFilterable) filterableTask).getIncludes());
            excludes = DefaultGroovyMethods.plus(excludes, ((PatternFilterable) filterableTask).getExcludes());
        }


        if (StringGroovyMethods.toBoolean(System.getProperty("idea.disable.gradle.resource.filtering", "false"))) {
            return new ArrayList<List<? extends Serializable>>(Arrays.asList(includes, excludes, filterReaders));
        }


        try {
            if (filterableTask instanceof ContentFilterable && DefaultGroovyMethods.getMetaClass(filterableTask).respondsTo(filterableTask, "getMainSpec")) {
                //noinspection GrUnresolvedAccess
                Object properties = DefaultGroovyMethods.invokeMethod(filterableTask, "getMainSpec", new Object[0]).properties;
                final Object actions = (properties == null ? null : properties.allCopyActions);
                Object copyActions = actions ? actions : (properties == null ? null : properties.copyActions);

                if (copyActions.asBoolean()) {
                    DefaultGroovyMethods.each(copyActions, new Closure<List<DefaultExternalFilter>>(null, null) {
                        public List<DefaultExternalFilter> doCall(Action<? super FileCopyDetails> action) {
                            Class filterClass = findPropertyWithType(action, Class.class, "val$filterType", "arg$2", "arg$1");
                            if (filterClass != null) {
                                //noinspection GrUnresolvedAccess
                                String filterType = filterClass.getName();
                                DefaultExternalFilter filter = new DefaultExternalFilter() {
                                };

                                Map props = findPropertyWithType(action, Map.class, "val$properties", "arg$1");
                                if (props != null) {
                                    if ("org.apache.tools.ant.filters.ExpandProperties".equals(filterType) && props.get("project")) {
                                        if (DefaultGroovyMethods.asBoolean(props.get("project"))) {
                                            filter.propertiesAsJsonMap = new GsonBuilder().create().toJson(DefaultGroovyMethods.getProperties(props.get("project")));
                                        }
                                    }
                                    else {
                                        filter.propertiesAsJsonMap = new GsonBuilder().create().toJson(props);
                                    }
                                }

                                return DefaultGroovyMethods.leftShift(filterReaders, filter);
                            }
                            else if (action.getClass().getSimpleName().equals("RenamingCopyAction") && DefaultGroovyMethods.hasProperty(action, "transformer")) {
                                //noinspection GrUnresolvedAccess
                                if (DefaultGroovyMethods.hasProperty(action.transformer, "matcher") && DefaultGroovyMethods.hasProperty((action == null
                                                ? null
                                                : action.transformer),
                                        "replacement")) {
                                    //noinspection GrUnresolvedAccess
                                    final Object transformer = (action == null ? null : action.transformer);
                                    final Object pattern1 = (transformer == null ? null : transformer.matcher).invokeMethod("pattern", new Object[0]);
                                    String pattern = (pattern1 == null ? null : pattern1.pattern);
                                    //noinspection GrUnresolvedAccess
                                    final Object transformer1 = (action == null ? null : action.transformer);
                                    String replacement = (transformer1 == null ? null : transformer1.replacement);
                                    DefaultExternalFilter filter = new DefaultExternalFilter() {
                                    };
                                    if (pattern && replacement) {
                                        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
                                        map.put("pattern", pattern);
                                        map.put("replacement", replacement);
                                        filter.propertiesAsJsonMap = new GsonBuilder().create().toJson(map);
                                        return DefaultGroovyMethods.leftShift(filterReaders, filter);
                                    }
                                }
                            }

//          else {
//            project.logger.error(
//              ErrorMessageBuilder.create(project, "Resource configuration errors")
//                .withDescription("Unsupported copy action found: " + action.class.name).build())
//          }
                        }
                    });
                }
            }
        }
        catch (Exception ignore) {
//      project.logger.error(
//        ErrorMessageBuilder.create(project, e, "Resource configuration errors")
//          .withDescription("Unable to resolve resources filtering configuration").build())
        }


        return new ArrayList<List<? extends Serializable>>(Arrays.asList(includes, excludes, filterReaders));
    }

    public static <T> T findPropertyWithType(Object self, Class<T> type, String... propertyNames) {
        for (String name : propertyNames) {
            MetaProperty property = DefaultGroovyMethods.hasProperty(self, name);
            if (property != null) {
                Object value = property.getProperty(self);
                if (type.isAssignableFrom(value.getClass())) {
                    return (DefaultGroovyMethods.asType(value, getProperty("T")));
                }
            }
        }

        return null;
    }

    private static String wrap(Object o) {
        return o instanceof CharSequence ? o.toString() : "";
    }

    @NotNull
    @Override
    public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
        return ErrorMessageBuilder.create(project, e, "Project resolve errors").withDescription("Unable to resolve additional project configuration.");
    }

    public static ModelBuilderContext.DataProvider<Map<Project, ExternalProject>> getPROJECTS_PROVIDER() {
        return PROJECTS_PROVIDER;
    }

    private static final boolean                                                         is4OrBetter       =
            GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.0")) >= 0;
    private static final ModelBuilderContext.DataProvider<Map<Project, ExternalProject>> PROJECTS_PROVIDER = new ModelBuilderContext.DataProvider<Map<Project, ExternalProject>>() {
        @NotNull
        @Override
        public Map<Project, ExternalProject> create(@NotNull Gradle gradle) {
            return new HashMap<Project, ExternalProject>();
        }
    };
}
