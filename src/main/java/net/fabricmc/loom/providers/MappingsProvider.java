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

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.PathUtils;
import net.fabricmc.loom.util.Version;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandMergeTiny;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.stitch.commands.CommandReorderTiny;
import net.fabricmc.stitch.commands.tinyv2.CommandMergeTinyV2;
import net.fabricmc.stitch.commands.tinyv2.CommandProposeV2FieldNames;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;


public class MappingsProvider extends DependencyProvider {
    public MinecraftMappedProvider mappedProvider;

    public String mappingsName;
    public String minecraftVersion;
    public String mappingsVersion;

    private boolean isV2;
    private Path mappingsDir;
    private Path mappingsStepsDir;
    private Path tinyMappingsWithoutEnums;
    public File tinyMappings;
    public File mappingsMixinExport;

    public void clean() throws IOException {
        tinyMappings.delete();
        PathUtils.deleteDirectoryContents(mappingsStepsDir);
    }

//	public boolean mappingsExist(){
//		return tinyMappings.exists();
//	}

    public TinyTree getMappings() throws IOException {
        return MappingsCache.INSTANCE.get(tinyMappings.toPath());
    }

    @Nullable
    public TinyTree getMappingsOfVersion(Project project, String version) throws IOException {
        Path migrateMappingsDir = mappingsDir.resolve("migrate");
        Path localMappingsOfVersion = migrateMappingsDir.resolve(version + ".tiny");
        if (Files.exists(localMappingsOfVersion)) {
            try (BufferedReader reader = Files.newBufferedReader(localMappingsOfVersion)) {
                return TinyMappingFactory.loadWithDetection(reader);
            }
        } else {
            //TODO: download unmerged v2 mappings once they are on the maven (yarn-unmerged:v2)
            String versionRelativePath = version.replace("+", "%2B").replace(" ", "%20");
            String artifactUrl = "https://maven.fabricmc.net/net/fabricmc/yarn/" + versionRelativePath + "/yarn-" + versionRelativePath + ".jar";
            File mappingsJar = File.createTempFile("migrateMappingsJar", ".jar");
            project.getLogger().lifecycle(":downloading new mappings from " + artifactUrl);
            FileUtils.copyURLToFile(new URL(artifactUrl), mappingsJar);
            try (FileSystem jar = FileSystems.newFileSystem(mappingsJar.toPath(), null)) {
            	if(!Files.exists(migrateMappingsDir)) Files.createDirectory(migrateMappingsDir);
                extractMappings(jar, localMappingsOfVersion);
                try (BufferedReader reader = Files.newBufferedReader(localMappingsOfVersion)) {
                    return TinyMappingFactory.loadWithDetection(reader);
                }
            }
        }

    }

    @Override
    public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
        MinecraftProvider minecraftProvider = getDependencyManager().getProvider(MinecraftProvider.class);

        project.getLogger().lifecycle(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

        String version = dependency.getResolvedVersion();
        List<File> mappingsJars = new ArrayList<>(dependency.resolve());
        if (mappingsJars.isEmpty()) {
            throw new RuntimeException("Could not find dependency " + dependency);
        }
        this.isV2 = containsV2Yarn(mappingsJars);


        this.mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");
        if (this.isV2) mappingsName += "-v2";

        Version mappingsVersion = new Version(version);
        this.minecraftVersion = mappingsVersion.getMinecraftVersion();
        this.mappingsVersion = mappingsVersion.getMappingsVersion();

        initFiles(project);

        PathUtils.createDirectoryWhenMissing(mappingsDir);
        PathUtils.createDirectoryWhenMissing(mappingsStepsDir);

        if (!tinyMappings.exists()) {
            storeMappings(project, minecraftProvider, mappingsJars);
        }

        mappedProvider = new MinecraftMappedProvider();
        mappedProvider.initFiles(project, minecraftProvider, this);
        mappedProvider.provide(dependency, project, extension, postPopulationScheduler);
    }

    private boolean containsV2Yarn(List<File> mappingsJars) {
        if (mappingsJars.size() == 1) return false;
        else if (mappingsJars.size() == 3) {
            return FilenameUtils.removeExtension(getUnmergedYarnJar(mappingsJars).getName()).endsWith("-v2");
        } else {
            throw new RuntimeException("Found an unexpected amount of mapping jars: " + mappingsJars.size() + ". This is likely because your 'mappings' line in build.gradle is incorrect.");
        }
    }

    private File getUnmergedYarnJar(List<File> mappingsJars) {
        return mappingsJars.stream()
                .filter(f -> f.getName().startsWith("yarn-unmerged")).findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find unmerged yarn mappings. Mappings: " + mappingsJars));
    }

    private void storeMappings(Project project, MinecraftProvider minecraftProvider, List<File> mappingsJars) throws IOException {

        if (mappingsJars.size() == 1) {
            // These are merged v1 mappings
            saveMergedV1Mappings(project, mappingsJars.get(0).toPath());
        } else if (mappingsJars.size() == 3) {
            // These are unmerged v1 mappings or v2 mappings
            File unmergedYarn = getUnmergedYarnJar(mappingsJars);
            File unmergedIntermediary = mappingsJars.stream()
                    .filter(f -> {
                        String name = FilenameUtils.removeExtension(f.getName());
                        boolean isV2Intermediary = name.endsWith("-v2");
                        return name.startsWith("intermediary") && isV2 == isV2Intermediary;
                    }).findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find unmerged intermediary mappings. Mappings: " + mappingsJars));


            mergeAndSaveMappings(project, unmergedIntermediary.toPath(), unmergedYarn.toPath());
        } else {
            throw new RuntimeException("Found an unexpected amount of mapping jars: " + mappingsJars.size());
        }


        if (tinyMappings.exists()) {
            tinyMappings.delete();
        }

        project.getLogger().lifecycle(":populating field names");
        suggestFieldNames(minecraftProvider, tinyMappingsWithoutEnums, tinyMappings.toPath());

    }

    private void saveMergedV1Mappings(Project project, Path mappingsJar) throws IOException {
        project.getLogger().lifecycle(":extracting " + mappingsJar.getFileName());
        try (FileSystem fileSystem = FileSystems.newFileSystem(mappingsJar, null)) {
            extractMappings(fileSystem, tinyMappingsWithoutEnums);
        }
    }

    private void extractMappings(FileSystem jar, Path extractTo) throws IOException {
        Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
    }

    private String extension() {
        return isV2 ? ".tiny2" : ".tiny";
    }

    private void mergeAndSaveMappings(Project project, Path unmergedIntermediaryJar, Path unmergedYarnJar) throws IOException {
        Path unmergedIntermediary = Paths.get(mappingsStepsDir.toString(), "unmerged-intermediary" + extension());
        project.getLogger().lifecycle(":extracting " + unmergedIntermediaryJar.getFileName().toString());
        try (FileSystem unmergedIntermediaryFs = FileSystems.newFileSystem(unmergedIntermediaryJar, null)) {
            extractMappings(unmergedIntermediaryFs, unmergedIntermediary);
        }

        Path unmergedYarn = Paths.get(mappingsStepsDir.toString(), "unmerged-yarn" + extension());
        project.getLogger().lifecycle(":extracting " + unmergedYarnJar.getFileName().toString());
        try (FileSystem unmergedYarnJarFs = FileSystems.newFileSystem(unmergedYarnJar, null)) {
            extractMappings(unmergedYarnJarFs, unmergedYarn);
        }

        Path invertedIntermediary = Paths.get(mappingsStepsDir.toString(), "inverted-intermediary" + extension());
        reorderMappings(unmergedIntermediary, invertedIntermediary, "intermediary", "official");
        Path unorderedMergedMappings = Paths.get(mappingsStepsDir.toString(), "unordered-merged" + extension());
        project.getLogger().lifecycle(":merging");
        mergeMappings(invertedIntermediary, unmergedYarn, unorderedMergedMappings);
        reorderMappings(unorderedMergedMappings, tinyMappingsWithoutEnums, "official", "intermediary", "named");
    }

    private void reorderMappings(Path oldMappings, Path newMappings, String... newOrder) {
        Command command = isV2 ? new CommandReorderTinyV2() : new CommandReorderTiny();
        String[] args = new String[2 + newOrder.length];
        args[0] = oldMappings.toAbsolutePath().toString();
        args[1] = newMappings.toAbsolutePath().toString();
        System.arraycopy(newOrder, 0, args, 2, newOrder.length);
        runCommand(command, args);
    }

    private void mergeMappings(Path intermediaryMappings, Path yarnMappings, Path newMergedMappings) {
        Command command = isV2 ? new CommandMergeTinyV2() : new CommandMergeTiny();
        runCommand(command, intermediaryMappings.toAbsolutePath().toString(),
                yarnMappings.toAbsolutePath().toString(),
                newMergedMappings.toAbsolutePath().toString(),
                "intermediary", "official");
    }

    private void suggestFieldNames(MinecraftProvider minecraftProvider, Path oldMappings, Path newMappings) {
        Command command = isV2 ? new CommandProposeV2FieldNames() : new CommandProposeFieldNames();
        runCommand(command, minecraftProvider.MINECRAFT_MERGED_JAR.getAbsolutePath(),
                oldMappings.toAbsolutePath().toString(),
                newMappings.toAbsolutePath().toString());
    }

    private void runCommand(Command command, String... args) {
        try {
            command.run(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void initFiles(Project project) {
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        mappingsDir = extension.getUserCache().toPath().resolve("mappings");
        mappingsStepsDir = mappingsDir.resolve("steps");

        tinyMappingsWithoutEnums = mappingsStepsDir.resolve("merged-ordered" + extension());
        tinyMappings = mappingsDir.resolve(String.format("%s-tiny-%s-%s.tiny", mappingsName, minecraftVersion, mappingsVersion)).toFile();
        mappingsMixinExport = new File(extension.getProjectBuildCache(), "mixin-map-" + minecraftVersion + "-" + mappingsVersion + ".tiny");
    }

    @Override
    public String getTargetConfig() {
        return Constants.MAPPINGS;
    }
}
