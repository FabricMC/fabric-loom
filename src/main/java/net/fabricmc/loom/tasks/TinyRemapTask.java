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
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by covers1624 on 7/02/19.
 */
public class TinyRemapTask extends CachedInputTask {

    private Object input;
    private Object output;
    private Object libraries;
    private List<Object> mappings = new ArrayList<>();
    private Object fromMappings;
    private Object toMappings;

    @TaskAction
    public void doTask() throws IOException {
        File input = getInput();
        File output = getOutput();
        boolean isOverwrite = input.equals(output);

        if (isOverwrite) {
            if (input.isFile()) {
                output = new File(getTemporaryDir(), "out.jar");
            } else {
                throw new RuntimeException("TODO, Overwriting directories.");
            }
        }

        TinyRemapper.Builder builder = TinyRemapper.newRemapper();
        builder.renameInvalidLocals(true);
        builder.rebuildSourceFilenames(true);
        for (File mappings : getMappings()) {
            builder.withMappings(TinyUtils.createTinyMappingProvider(mappings.toPath(), getFromMappings(), getToMappings()));
        }

        TinyRemapper remapper = builder.build();
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath(output.toPath())) {
            Path inputPath = input.toPath();
            outputConsumer.addNonClassFiles(inputPath);
            remapper.read(inputPath);
            remapper.read(getLibraries().getFiles().stream().map(File::toPath).toArray(Path[]::new));
            remapper.apply(inputPath, outputConsumer);
        } finally {
            remapper.finish();
        }

        if (isOverwrite) {
            input.delete();
            output.renameTo(input);
        }
    }

    //@formatter:off
    @CachedInput public File getInput() { return getProject().file(input); }
    @OutputFile public File getOutput() { return getProject().file(output); }
    @CachedInput public FileCollection getLibraries() { return getProject().files(libraries); }
    @CachedInput public List<File> getMappings() {
        return mappings.stream()//TODO, this is messy.
                .filter(e -> !(e instanceof File) || ((File) e).exists())
                .map(getProject()::file)
                .collect(Collectors.toList());
    }
    @CachedInput public String getFromMappings() { return Utils.resolveString(fromMappings); }
    @CachedInput public String getToMappings() { return Utils.resolveString(toMappings); }
    public void setInput(Object input) { this.input = input; }
    public void setOutput(Object output) { this.output = output; }
    public void setLibraries(Object libraries) { this.libraries = libraries; }
    public void addMappings(Object mappings) { this.mappings.add(mappings); }
    public void setFromMappings(Object fromMappings) { this.fromMappings = fromMappings; }
    public void setToMappings(Object toMappings) { this.toMappings = toMappings; }
    //@formatter:on
}
