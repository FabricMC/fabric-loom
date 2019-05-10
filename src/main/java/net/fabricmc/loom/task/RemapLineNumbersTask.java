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

import net.fabricmc.loom.task.fernflower.FernFlowerTask;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.loom.util.progress.ProgressLogger;
import net.fabricmc.stitch.util.StitchUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class RemapLineNumbersTask extends AbstractLoomTask {
    private Object input;
    private Object output;
    private Object lineMapFile;

    @TaskAction
    public void doTask() throws Throwable {
        Project project = getProject();

        project.getLogger().lifecycle(":adjusting line numbers");
        LineNumberRemapper remapper = new LineNumberRemapper();
        remapper.readMappings(getLineMapFile());

        ProgressLogger progressLogger = ProgressLogger.getProgressFactory(project, FernFlowerTask.class.getName());
        progressLogger.start("Adjusting line numbers", "linemap");

        try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(getInput(), true);
             StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(getOutput(), true)) {
            remapper.process(progressLogger, inFs.get().getPath("/"), outFs.get().getPath("/"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        progressLogger.completed();
    }

    //@formatter:off
    @Input public File getInput() { return getProject().file(input); }
    @InputFile public File getLineMapFile() { return getProject().file(lineMapFile); }
    @OutputFile public File getOutput() { return getProject().file(output); }
    public void setInput(Object input) { this.input = input; }
    public void setLineMapFile(Object lineMapFile) { this.lineMapFile = lineMapFile; }
    public void setOutput(Object output) { this.output = output; }
    //@formatter:on
}
