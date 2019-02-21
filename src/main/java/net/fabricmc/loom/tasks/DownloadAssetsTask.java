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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.data.AssetIndexJson;
import net.fabricmc.loom.tasks.download.DownloadAction;
import net.fabricmc.loom.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.text.MessageFormat.format;
import static net.fabricmc.loom.util.Utils.sneaky;

/**
 * Created by covers1624 on 5/02/19.
 */
public class DownloadAssetsTask extends DefaultTask {

    private Object assetIndex;
    private File assetsDir;
    private List<File> outputFiles = new ArrayList<>();

    private transient Map<String, DownloadAction> toExecute = new HashMap<>();
    private transient AssetIndexJson assetIndexJson;

    public DownloadAssetsTask() {
        onlyIf(e -> {
            //Use this onlyIf section to lazy resolve the DownloadAction's and declare outputs.
            resolve();
            return true;
        });
    }

    @TaskAction
    public void doTask() throws Exception {
        ServiceRegistry registry = ((ProjectInternal) getProject()).getServices();
        ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
        ProgressLogger progressGroup = factory.newOperation(getClass()).setDescription("Download Assets");
        Supplier<ProgressLogger> loggerFactory = () -> {
            ProgressLogger pl = factory.newOperation(getClass(), progressGroup);
            pl.setDescription("Download worker");
            pl.started();
            return pl;
        };
        progressGroup.started();
        Stack<ProgressLogger> freeLoggers = new Stack<>();
        ProgressLogger statusLogger = loggerFactory.get();
        statusLogger.progress("Waiting..");
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        //submit all actions to the executor.
        toExecute.forEach((name, action) ->//
                executor.submit(() -> {
                    ProgressLogger logger;
                    //attempt to pop a free logger from the stack.
                    if (!freeLoggers.isEmpty()) {
                        synchronized (freeLoggers) {
                            logger = freeLoggers.pop();
                        }
                    } else {
                        //no free loggers, make one.
                        logger = loggerFactory.get();
                    }
                    logger.progress(name);
                    //Create a wrapper over DownloadAction's wrapper.
                    //progressGroup > logger > wrapper
                    //downoadAction > asset > progress
                    action.setProgressLogger(factory.newOperation(getClass(), logger).setDescription(name));
                    sneaky(action::execute);//do the do
                    action.setProgressLogger(null);
                    logger.progress("Idle..");
                    synchronized (freeLoggers) {//push the free logger back to the stack.
                        freeLoggers.push(logger);
                    }
                }));
        //Trigger a soft shutodown of the executor.
        executor.shutdown();
        int max = (int) executor.getTaskCount();
        //while it hasn't finished, update our fancy logger.
        while (!executor.awaitTermination(200, TimeUnit.MILLISECONDS)) {
            int done = (int) executor.getCompletedTaskCount();
            statusLogger.progress(format("Completed: {0}/{1}   {2}%", done, max, (int) ((double) done / max * 100)));
        }
        statusLogger.completed();
        freeLoggers.forEach(ProgressLogger::completed);
        progressGroup.completed();
    }

    private void resolve() {
        if (assetIndexJson != null) {
            return;
        }
        File assetIndex = getAssetIndex();
        String indexName = Files.getNameWithoutExtension(assetIndex.getName());
        assetIndexJson = AssetIndexJson.fromJson(assetIndex);
        assetIndexJson.objects.forEach((name, object) -> {
            DownloadAction action = new DownloadAction(getProject());

            String loc = object.hash.substring(0, 2) + "/" + object.hash;
            File out;
            if (!assetIndexJson.virtual) {
                out = new File(assetsDir, "objects/" + loc);
            } else {
                out = new File(assetsDir, format("virtual/{0}/{1}", indexName, name));
            }

            action.setSrc(LoomGradlePlugin.RESOURCES_URL + loc);
            action.setDest(out);
            action.setQuiet(true);
            //Always declare the outputs.
            outputFiles.add(out);
            //Check if we need to re download the file.
            if (out.exists()) {
                //Mojang still uses SHA1 for their assets.
                @SuppressWarnings ("deprecation")
                Hasher hasher = Hashing.sha1().newHasher();
                Utils.addToHasher(hasher, out);
                if (hasher.hash().toString().equals(object.hash)) {
                    return;//from the lambda, basically continue.
                }
            }
            //File doesnt exist or is corrupt.
            toExecute.put(name, action);
        });
    }

    //@formatter:off
    @Input public File getAssetIndex() { return getProject().file(assetIndex); }
    @OutputFiles public List<File> getOutputFiles() { return outputFiles; }
    public void setAssetIndex(Object assetIndex) { this.assetIndex = assetIndex; }
    public void setAssetsDir(File assetsDir) { this.assetsDir = assetsDir; }
    //@formatter:on
}
