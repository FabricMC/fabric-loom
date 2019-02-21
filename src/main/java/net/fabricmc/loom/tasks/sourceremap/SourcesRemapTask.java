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

package net.fabricmc.loom.tasks.sourceremap;

import net.fabricmc.loom.tasks.TForkingJavaExecTask;
import net.fabricmc.loom.tasks.cache.CachedInput;
import net.fabricmc.loom.tasks.cache.CachedInputTask;
import net.fabricmc.loom.util.Utils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by covers1624 on 9/02/19.
 */
public class SourcesRemapTask extends CachedInputTask implements TForkingJavaExecTask {

    private List<Object> mappings = new ArrayList<>();
    private Object fromMappings;
    private Object toMappings;
    private Object libraries;
    private Object inputFile;
    private Object outputFile;

    @TaskAction
    public void doTask() throws Throwable {
        List<String> args = new ArrayList<>();
        getLibraries().forEach(f -> args.add("-l=" + f.getAbsolutePath()));
        args.add("-i=" + getInput().getAbsolutePath());
        args.add("-o=" + getOutput().getAbsolutePath());
        getMappings().forEach(f -> args.add("-m=" + f.getAbsolutePath()));
        args.add("-f=" + getFromMappings());
        args.add("-t=" + getToMappings());
        ExecResult result = javaexec(spec -> {
            spec.setMain(ForkedMercuryExecutor.class.getName());
            spec.setArgs(args);
            spec.jvmArgs("-Xms200m", "-Xmx3G");
            spec.setErrorOutput(System.err);
            spec.setStandardOutput(System.out);
        });
        result.rethrowFailure();
        result.assertNormalExitValue();
    }

    //@formatter:off
    @CachedInput public List<File> getMappings() {
        return mappings.stream()//TODO, this is messy.
                .filter(e -> !(e instanceof File) || ((File) e).exists())
                .map(getProject()::file)
                .collect(Collectors.toList());
    }
    @CachedInput public String getFromMappings() { return Utils.resolveString(fromMappings); }
    @CachedInput public String getToMappings() { return Utils.resolveString(toMappings); }
    @CachedInput public FileCollection getLibraries() { return getProject().files(libraries); }
    @CachedInput public File getInput() { return getProject().file(inputFile); }
    @OutputFile public File getOutput() { return getProject().file(outputFile); }
    public void addMappings(Object mappings) { this.mappings.add(mappings); }
    public void setFromMappings(Object fromMappings) { this.fromMappings = fromMappings; }
    public void setToMappings(Object toMappings) { this.toMappings = toMappings; }
    public void setLibraries(Object libraries) { this.libraries = libraries; }
    public void setInput(Object inputFile) { this.inputFile = inputFile; }
    public void setOutput(Object outputFile) { this.outputFile = outputFile; }
    //@formatter:on

}
