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

package net.fabricmc.loom.tasks.download;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.progress.ProgressLogger;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Inspired and vaguely based off https://github.com/michel-kraemer/gradle-download-task
 * <pre>
 * Notable differences:
 *  Wayyy simpler implementation.
 *  Lazy evaluation of file & URL inputs.
 *  Single file downloads.
 *  External validation of file for up-to-date checking.
 *  UserAgent spoofing. (Thanks mojang!)
 *  Ability to set the ProgressLogger to use.
 * </pre>
 *
 * This is split into an Action, Spec and Task.
 *
 * The Spec {@link DownloadSpec}, Provides the specification for how things work.
 *
 * The Action {@link DownloadAction}, What actually handles downloading
 * implements {@link DownloadSpec}, Useful for other tasks that need to download
 * something but not necessarily create an entire task to do said download.
 *
 * The Task {@link DownloadTask}, Task wrapper for {@link DownloadAction},
 * implements {@link DownloadSpec} and hosts the Action as a task.
 *
 * Created by covers1624 on 8/02/19.
 */
public class DownloadTask extends DefaultTask implements DownloadSpec {

    private final DownloadAction action;

    public DownloadTask() {
        action = new DownloadAction(getProject());
        getOutputs().upToDateWhen(e -> false);//Always run, we set our self to up-to-date after checks.
    }

    @TaskAction
    public void doTask() throws IOException {
        action.execute();
        //We always execute, but 'spoof' our self as up-to-date after etag &| onlyIfModified checks.
        if (action.isUpToDate()) {
            getState().setOutcome(TaskExecutionOutcome.UP_TO_DATE);
            setDidWork(false);
        }
    }

    //@formatter:off
    @Override public void fileUpToDateWhen(Spec<File> spec) { action.fileUpToDateWhen(spec); }
    @Override public URL getSrc() { return action.getSrc(); }
    @OutputFile @Override public File getDest() { return action.getDest(); }
    @Override public boolean getOnlyIfModified() { return action.getOnlyIfModified(); }
    @Override public DownloadAction.UseETag getUseETag() { return action.getUseETag(); }
    @Override public File getETagFile() { return action.getETagFile(); }
    @Override public String getUserAgent() { return action.getUserAgent(); }
    @Override public boolean isQuiet() { return action.isQuiet(); }
    @Override public boolean isUpToDate() { return action.isUpToDate(); }
    @Override public void setSrc(Object src) { action.setSrc(src); }
    @Override public void setDest(Object dest) { action.setDest(dest); }
    @Override public void setOnlyIfModified(boolean onlyIfModified) { action.setOnlyIfModified(onlyIfModified); }
    @Override public void setUseETag(Object useETag) { action.setUseETag(useETag); }
    @Override public void setETagFile(Object eTagFile) { action.setETagFile(eTagFile); }
    @Override public void setUserAgent(String userAgent) { action.setUserAgent(userAgent); }
    @Override public void setQuiet(boolean quiet) { action.setQuiet(quiet); }
    @Override public void setProgressLogger(ProgressLogger logger) { action.setProgressLogger(logger); }
    //@formatter:on
}
