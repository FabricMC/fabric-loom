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
import net.fabricmc.stitch.merge.JarMerger;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Created by covers1624 on 5/02/19.
 */
public class MergeJarTask extends CachedInputTask {

    private Object clientJar;
    private Object serverJar;
    private Object mergedJar;

    private boolean removeSnowmen = false;
    private boolean offsetSyntheticParams = false;

    @TaskAction
    public void doTask() throws IOException {
        try (JarMerger merger = new JarMerger(getClientJar(), getServerJar(), getMergedJar())) {
            if (getRemoveSnowmen()) {
                merger.enableSnowmanRemoval();
            }
            if (getOffsetSyntheticParams()) {
                merger.enableSyntheticParamsOffset();
            }
            merger.merge();
        }
    }

    //@formatter:off
    @CachedInput public File getClientJar() { return getProject().file(clientJar); }
    @CachedInput public File getServerJar() { return getProject().file(serverJar); }
    @OutputFile public File getMergedJar() { return getProject().file(mergedJar); }
    @CachedInput public boolean getRemoveSnowmen() { return removeSnowmen; }
    @CachedInput public boolean getOffsetSyntheticParams() { return offsetSyntheticParams; }
    public void setClientJar(Object clientJar) { this.clientJar = clientJar; }
    public void setServerJar(Object serverJar) { this.serverJar = serverJar; }
    public void setMergedJar(Object mergedJar) { this.mergedJar = mergedJar; }
    public void setRemoveSnowmen(boolean removeSnowmen) { this.removeSnowmen = removeSnowmen; }
    public void setOffsetSyntheticParams(boolean offsetSyntheticParams) { this.offsetSyntheticParams = offsetSyntheticParams; }
    //@formatter:on
}

