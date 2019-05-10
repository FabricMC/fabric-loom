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
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Version;
import net.fabricmc.mappings.*;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MigrateMappingsTask extends AbstractLoomTask {
    @TaskAction
    public void doTask() throws Throwable {
        Project project = getProject();
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        Map<String, ?> properties = project.getProperties();

        project.getLogger().lifecycle(":loading mappings");

        File mappingsFile = null;

        if (properties.containsKey("targetMappingsFile")) {
            mappingsFile = new File((String) properties.get("targetMappingsFile"));
        } else if (properties.containsKey("targetMappingsArtifact")) {
            String[] artifactName = ((String) properties.get("targetMappingsArtifact")).split(":");
            if (artifactName.length != 3) {
                throw new RuntimeException("Invalid artifact name: " + properties.get("targetMappingsArtifact"));
            }

            String mappingsName = artifactName[0] + "." + artifactName[1];

            Version v = new Version(artifactName[2]);
            String minecraftVersion = v.getMinecraftVersion();
            String mappingsVersion = v.getMappingsVersion();

            mappingsFile = new File(extension.getMappingsProvider().MAPPINGS_DIR, mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion);
        }

        if (mappingsFile == null || !mappingsFile.exists()) {
            throw new RuntimeException("Could not find mappings file: " + (mappingsFile != null ? mappingsFile : "null"));
        }

        if (!properties.containsKey("inputDir") || !properties.containsKey("outputDir")) {
            throw new RuntimeException("Must specify input and output dir!");
        }

        File inputDir = new File((String) properties.get("inputDir"));
        File outputDir = new File((String) properties.get("outputDir"));

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new RuntimeException("Could not find input directory: " + inputDir);
        }

        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new RuntimeException("Could not create output directory:"  + outputDir);
            }
        }

        Mappings sourceMappings = extension.getMappingsProvider().getMappings();
        Mappings targetMappings;

        try (FileInputStream stream = new FileInputStream(mappingsFile)) {
            targetMappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false);
        }

        project.getLogger().lifecycle(":joining mappings");
        MappingSet mappingSet = new MappingsJoiner(sourceMappings, targetMappings, "intermediary", "named").read();

        project.getLogger().lifecycle(":remapping");
        Mercury mercury = new Mercury();

        for (File file : project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES).getFiles()) {
            mercury.getClassPath().add(file.toPath());
        }

        for (File file : project.getConfigurations().getByName(Constants.COMPILE_MODS_MAPPED).getFiles()) {
            mercury.getClassPath().add(file.toPath());
        }

        mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_MAPPED_JAR.toPath());
        mercury.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_INTERMEDIARY_JAR.toPath());

        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

        try {
            mercury.rewrite(inputDir.toPath(), outputDir.toPath());
        } catch (Exception e) {
            project.getLogger().warn("Could not remap fully!", e);
        }

        project.getLogger().lifecycle(":cleaning file descriptors");
        System.gc();
    }

    public static class MappingsJoiner extends MappingsReader {
        private final Mappings sourceMappings, targetMappings;
        private final String fromNamespace, toNamespace;

        public MappingsJoiner(Mappings sourceMappings, Mappings targetMappings, String fromNamespace, String toNamespace) {
            this.sourceMappings = sourceMappings;
            this.targetMappings = targetMappings;
            this.fromNamespace = fromNamespace;
            this.toNamespace = toNamespace;
        }

        @Override
        public MappingSet read(MappingSet mappings) throws IOException {
            Map<String, ClassEntry> targetClasses = new HashMap<>();
            Map<EntryTriple, FieldEntry> targetFields = new HashMap<>();
            Map<EntryTriple, MethodEntry> targetMethods = new HashMap<>();

            targetMappings.getClassEntries().forEach((c) -> targetClasses.put(c.get(fromNamespace), c));
            targetMappings.getFieldEntries().forEach((c) -> targetFields.put(c.get(fromNamespace), c));
            targetMappings.getMethodEntries().forEach((c) -> targetMethods.put(c.get(fromNamespace), c));

            for (ClassEntry entry : sourceMappings.getClassEntries()) {
                String from = entry.get(toNamespace);
                String to = targetClasses.getOrDefault(entry.get(fromNamespace), entry).get(toNamespace);

                mappings.getOrCreateClassMapping(from).setDeobfuscatedName(to);
            }

            for (FieldEntry entry : sourceMappings.getFieldEntries()) {
                EntryTriple fromEntry = entry.get(toNamespace);
                EntryTriple toEntry = targetFields.getOrDefault(entry.get(fromNamespace), entry).get(toNamespace);

                mappings.getOrCreateClassMapping(fromEntry.getOwner())
                        .getOrCreateFieldMapping(fromEntry.getName(), fromEntry.getDesc())
                        .setDeobfuscatedName(toEntry.getName());
            }

            for (MethodEntry entry : sourceMappings.getMethodEntries()) {
                EntryTriple fromEntry = entry.get(toNamespace);
                EntryTriple toEntry = targetMethods.getOrDefault(entry.get(fromNamespace), entry).get(toNamespace);

                mappings.getOrCreateClassMapping(fromEntry.getOwner())
                        .getOrCreateMethodMapping(fromEntry.getName(), fromEntry.getDesc())
                        .setDeobfuscatedName(toEntry.getName());
            }

            return mappings;
        }

        @Override
        public void close() throws IOException {

        }
    }
}
