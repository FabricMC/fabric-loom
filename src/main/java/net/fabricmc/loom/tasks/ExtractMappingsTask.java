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

package net.fabricmc.loom.tasks;

import net.fabricmc.loom.tasks.cache.CachedInput;
import net.fabricmc.loom.tasks.cache.CachedInputTask;
import net.fabricmc.loom.util.Utils;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Resolves the mappings provided to our Extension,
 * extracts them to baseMappings, proposes field names and
 * spits out the final mappings.
 * Created by covers1624 on 7/02/19.
 */
public class ExtractMappingsTask extends CachedInputTask {

    private Object mappingsArtifact;
    private Object mergedJar;
    private Object baseMappings;
    private Object mappings;

    @TaskAction
    public void doTask() throws Exception {
        //This is validated in LoomGradlePlugin.afterEvaluate
        Configuration config = getProject()//
                .getConfigurations()//
                .detachedConfiguration(getProject().getDependencies().module(getMappingsArtifact()));

        Optional<Dependency> dep = config.getDependencies().stream().findFirst();
        if (!dep.isPresent()) {
            throw new IllegalStateException("Unable to find mappings from configuration, should be impossible.");
        }
        File mappingsJar = config.files(dep.get()).stream().findFirst().orElseThrow(IllegalStateException::new);
        try (FileSystem fs = FileSystems.newFileSystem(mappingsJar.toPath(), null)) {
            Files.copy(fs.getPath("mappings/mappings.tiny"), getBaseMappings().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        //Bonk stderr to INFO for this task, CommandProposeFieldNames is a little aggressive.
        getLogging().captureStandardError(LogLevel.INFO);
        String[] args = { getMergedJar().getAbsolutePath(), getBaseMappings().getAbsolutePath(), getMappings().getAbsolutePath() };
        new CommandProposeFieldNames().run(args);
    }

    //@formatter:off
    @CachedInput public String getMappingsArtifact() { return Utils.resolveString(mappingsArtifact); }
    public File getMergedJar() { return getProject().file(mergedJar); }
    public File getBaseMappings() { return getProject().file(baseMappings); }
    @OutputFile public File getMappings() { return getProject().file(mappings); }
    public void setMappingsArtifact(Object mappingsArtifact) { this.mappingsArtifact = mappingsArtifact; }
    public void setBaseMappings(Object baseMappings) { this.baseMappings = baseMappings; }
    public void setMergedJar(Object mergedJar) { this.mergedJar = mergedJar; }
    public void setMappings(Object mappings) { this.mappings = mappings; }
    //@formatter:on

}
