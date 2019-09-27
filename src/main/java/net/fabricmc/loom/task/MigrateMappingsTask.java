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
import net.fabricmc.mapping.tree.*;
import net.fabricmc.stitch.util.Pair;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.Mapping;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

//TODO: the usage of this task is very hacky and barely works.
public class MigrateMappingsTask extends AbstractLoomTask {
    @TaskAction
    public void doTask() throws Throwable {
        Project project = getProject();
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        Map<String, ?> properties = project.getProperties();

        project.getLogger().lifecycle(":loading mappings");
//
//        Path mappingsFile = null;
//        Mappings mappings =
//
//        if (properties.containsKey("targetMappingsFile")) {
//            mappingsFile = Paths.get((String) properties.get("targetMappingsFile"));
//        } else if (properties.containsKey("targetMappingsArtifact")) {
//            String[] artifactName = ((String) properties.get("targetMappingsArtifact")).split(":");
//            if (artifactName.length != 3) {
//                throw new RuntimeException("Invalid artifact name: " + properties.get("targetMappingsArtifact"));
//            }
//
//            String mappingsName = artifactName[0] + "." + artifactName[1];
//
//            Version v = new Version(artifactName[2]);
//            String minecraftVersion = v.getMinecraftVersion();
//            String mappingsVersion = v.getMappingsVersion();
//
//            mappingsFile = Paths.get(extension.getMappingsProvider().mappingsDir, mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion);
//        }
//
//        if (mappingsFile == null || !mappingsFile.exists()) {
//            throw new RuntimeException("Could not find mappings file: " + (mappingsFile != null ? mappingsFile : "null"));
//        }
//
//        if (!properties.containsKey("inputDir") || !properties.containsKey("outputDir")) {
//            throw new RuntimeException("Must specify input and output dir!");
//        }
//
//        File inputDir = new File((String) properties.get("inputDir"));
//        File outputDir = new File((String) properties.get("outputDir"));
//
//        if (!inputDir.exists() || !inputDir.isDirectory()) {
//            throw new RuntimeException("Could not find input directory: " + inputDir);
//        }
//
//        if (!outputDir.exists()) {
//            if (!outputDir.mkdirs()) {
//                throw new RuntimeException("Could not create output directory:" + outputDir);
//            }
//        }
//
//        TinyTree sourceMappings = extension.getMappingsProvider().getMappings();
//        Mappings targetMappings;
//
//        try (BufferedReader reader = new BufferedReader(mappingsFile)) {
//            targetMappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false);
//        }
//
//        project.getLogger().lifecycle(":joining mappings");
//        MappingSet mappingSet = new MappingsJoiner(sourceMappings, targetMappings,
//                "intermediary", "named").read();
//
//        project.getLogger().lifecycle(":remapping");
//        Mercury mercury = new Mercury();
//
//        for (File file : project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES).getFiles()) {
//            mercury.getClassPath().add(file.toPath());
//        }
//
//        for (File file : project.getConfigurations().getByName("compileClasspath").getFiles()) {
//            mercury.getClassPath().add(file.toPath());
//        }
//
//        mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_MAPPED_JAR.toPath());
//        mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_INTERMEDIARY_JAR.toPath());
//
//        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));
//
//        try {
//            mercury.rewrite(inputDir.toPath(), outputDir.toPath());
//        } catch (Exception e) {
//            project.getLogger().warn("Could not remap fully!", e);
//        }
//
//        project.getLogger().lifecycle(":cleaning file descriptors");
//        System.gc();
    }


    public static class MappingsJoiner extends MappingsReader {
        private final TinyTree sourceMappings, targetMappings;
        private final String fromNamespace, toNamespace;

        public MappingsJoiner(TinyTree sourceMappings, TinyTree targetMappings, String fromNamespace, String toNamespace) {
            this.sourceMappings = sourceMappings;
            this.targetMappings = targetMappings;
            this.fromNamespace = fromNamespace;
            this.toNamespace = toNamespace;
        }

        private <T extends Descriptored> void mapDescriptored(Collection<T> fromDescriptored,
                                                              Map<Pair<String, String>, T> targetDescriptored,
                                                              BiFunction<String,String,Mapping> mapper) {
            for (T fromEntry : fromDescriptored) {
                String fromName = fromEntry.getName(toNamespace);
                String fromDescriptor = fromEntry.getDescriptor(toNamespace);
                String toName = targetDescriptored.getOrDefault(Pair.of(fromName, fromDescriptor), fromEntry).getName(toNamespace);

                mapper.apply(fromName,fromDescriptor).setDeobfuscatedName(toName);
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

            for (ClassDef classEntry : sourceMappings.getClasses()) {
                String from = classEntry.getName(toNamespace);
                String to = targetClasses.getOrDefault(classEntry.getName(fromNamespace), classEntry).getName(toNamespace);

                ClassMapping classMapping = mappings.getOrCreateClassMapping(from).setDeobfuscatedName(to);

                mapDescriptored(classEntry.getFields(), targetFields,classMapping::getOrCreateFieldMapping);
                mapDescriptored(classEntry.getMethods(), targetMethods,classMapping::getOrCreateMethodMapping);

            }
            return mappings;
        }

        @Override
        public void close() throws IOException {

        }
    }
}
