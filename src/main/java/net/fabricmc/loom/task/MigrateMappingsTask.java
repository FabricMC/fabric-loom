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

package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.Descriptored;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.Pair;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class MigrateMappingsTask extends AbstractLoomTask {
    @TaskAction
    public void doTask() throws Throwable {
        Project project = getProject();
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        Map<String, ?> properties = project.getProperties();

        String inputDir = (String) properties.get("inputDir");
        if (inputDir == null) inputDir = "src/main/java";
        String outputDir = (String) properties.get("outputDir");
        if (outputDir == null) outputDir = "remappedSrc";
        String targetMappingsVersion = (String) properties.get("targetMappings");
        if (targetMappingsVersion == null) {
            throw new IllegalArgumentException("You must specify a new mappings version with -PtargetMappings.");
        }

        Path inputDirPath = Paths.get(inputDir);
        Path outputDirPath = Paths.get(outputDir);

        if (!Files.exists(inputDirPath) || !Files.isDirectory(inputDirPath)) {
            throw new IllegalArgumentException("Could not find input directory: " + inputDir);
        }

        if (!Files.exists(outputDirPath)) Files.createDirectory(outputDirPath);


        MappingsProvider mappingsProvider = extension.getMappingsProvider();
        try {
            TinyTree currentMappings = mappingsProvider.getMappings();
            TinyTree targetMappings = mappingsProvider.getMappingsOfVersion(project, targetMappingsVersion);
            migrateMappings(project, extension.getMinecraftMappedProvider(), inputDirPath, outputDirPath, currentMappings, targetMappings);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not find mappings for version " + targetMappingsVersion, e);
        }

    }

    private static void migrateMappings(Project project, MinecraftMappedProvider minecraftMappedProvider,
                                        Path inputDir, Path outputDir, TinyTree currentMappings, TinyTree targetMappings) throws IOException {

        project.getLogger().lifecycle(":joining mappings");
        MappingSet mappingSet = new MappingsJoiner(currentMappings, targetMappings,
                "intermediary", "named").read();

        project.getLogger().lifecycle(":remapping");
        Mercury mercury = SourceRemapper.createMercuryWithClassPath(project, false);

        mercury.getClassPath().add(minecraftMappedProvider.MINECRAFT_MAPPED_JAR.toPath());
        mercury.getClassPath().add(minecraftMappedProvider.MINECRAFT_INTERMEDIARY_JAR.toPath());

        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

        try {
            mercury.rewrite(inputDir, outputDir);
        } catch (Exception e) {
            project.getLogger().warn("Could not remap fully!", e);
        }

        project.getLogger().lifecycle(":cleaning file descriptors");
        System.gc();
    }


    public static class MappingsJoiner extends MappingsReader {
        private final TinyTree sourceMappings, targetMappings;
        private final String fromNamespace, toNamespace;

        /**
         * Say A is the source mappings and B is the target mappings.
         * It does not map from intermediary to named but rather maps from named-A to named-B, by matching intermediary names.
         * It goes through all of the intermediary names of A, and for every such intermediary name, call it I,
         * matches the named mapping of I in A, with the named mapping of I in B.
         * <p>
         * As you might imagine, this requires intermediary mappings to be static across all versions, which they are. :volderthonk:
         */
        public MappingsJoiner(TinyTree sourceMappings, TinyTree targetMappings, String fromNamespace, String toNamespace) {
            this.sourceMappings = sourceMappings;
            this.targetMappings = targetMappings;
            this.fromNamespace = fromNamespace;
            this.toNamespace = toNamespace;
        }

        private <T extends Descriptored> void mapDescriptored(Collection<T> fromDescriptored,
                                                              Map<Pair<String, String>, T> targetDescriptored,
                                                              BiFunction<String, String, Mapping> mapper) {
            for (T fromEntry : fromDescriptored) {
                String fromName = fromEntry.getName(toNamespace);
                String fromDescriptor = fromEntry.getDescriptor(toNamespace);
                String toName = targetDescriptored.getOrDefault(Pair.of(fromName, fromDescriptor), fromEntry).getName(toNamespace);

                mapper.apply(fromName, fromDescriptor).setDeobfuscatedName(toName);
            }
        }

        @Override
        public MappingSet read(MappingSet mappings) {
            Map<String, ClassDef> targetClasses = new HashMap<>();
            Map<Pair<String, String>, FieldDef> targetFields = new HashMap<>();
            Map<Pair<String, String>, MethodDef> targetMethods = new HashMap<>();

            for (ClassDef classDef : targetMappings.getClasses()) {
                targetClasses.put(classDef.getName(fromNamespace), classDef);

                for (FieldDef field : classDef.getFields()) {
                    targetFields.put(Pair.of(field.getName(fromNamespace), field.getDescriptor(fromNamespace)), field);
                }

                for (MethodDef method : classDef.getMethods()) {
                    targetMethods.put(Pair.of(method.getName(fromNamespace), method.getDescriptor(fromNamespace)), method);
                }
            }

            for (ClassDef sourceClass : sourceMappings.getClasses()) {
                String namedMappingOfSourceMapping = sourceClass.getName(toNamespace);
                String namedMappingOfTargetMapping = targetClasses.getOrDefault(sourceClass.getName(fromNamespace), sourceClass).getName(toNamespace);

                ClassMapping classMapping = mappings.getOrCreateClassMapping(namedMappingOfSourceMapping).setDeobfuscatedName(namedMappingOfTargetMapping);

                if (namedMappingOfSourceMapping.equals("net/minecraft/text/LiteralText") || namedMappingOfSourceMapping.equals("net/minecraft/network/chat/TextComponent")) {
                    int x = 2;
                }

                mapDescriptored(sourceClass.getFields(), targetFields, classMapping::getOrCreateFieldMapping);
                mapDescriptored(sourceClass.getMethods(), targetMethods, classMapping::getOrCreateMethodMapping);

            }
            return mappings;
        }

        @Override
        public void close() throws IOException {

        }
    }
}
