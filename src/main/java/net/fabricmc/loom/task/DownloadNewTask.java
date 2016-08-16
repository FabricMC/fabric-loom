/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 FabricMC
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

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.progress.ProgressLogger;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Generic Download class compatible with ProgressLogger
 */
public class DownloadNewTask extends DefaultTask {

    @Input
    private Object output;

    @OutputFile
    private String url;
    private String taskName;

    @TaskAction
    public void download() throws IOException {
        File outputFile = getProject().file(getOutput());
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();

        getLogger().info("Downloading " + getURL());

        URL url = new URL(getURL());
        HttpURLConnection connect = (HttpURLConnection) url.openConnection();
        connect.setRequestProperty("User-Agent", Constants.USER_AGENT);
        connect.setInstanceFollowRedirects(true);

        ProgressLogger progressLogger = ProgressLogger.getProgressFactory(getProject(), getClass().getName());
        progressLogger.setDescription("Downloading " + getURL());
        ReadableByteChannel inChannel = new DownloadChannel(Channels.newChannel(connect.getInputStream()), getContentLength(url), progressLogger);
        FileChannel outChannel = new FileOutputStream(outputFile).getChannel();
        outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
        outChannel.close();
        inChannel.close();
        progressLogger.completed();
        getLogger().info("Download complete");
    }

    private int getContentLength(URL url) {
        HttpURLConnection connection;
        int contentLength = -1;
        try {
            connection = (HttpURLConnection) url.openConnection();
            contentLength = connection.getContentLength();
        } catch (Exception e) {
        }
        return contentLength;
    }

    public File getOutput() {
        return getProject().file(output);
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public String getURL() {
        return url;
    }

    public void setURL(String url) {
        this.url = url;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskName() {
        return taskName;
    }

    class DownloadChannel implements ReadableByteChannel {
        ProgressLogger logger;
        String formattedSize;
        ReadableByteChannel rbc;
        long totalBytes;

        DownloadChannel(ReadableByteChannel rbc, long expectedSize, ProgressLogger logger) {
            this.logger = logger;
            this.formattedSize = toHumanReadableLength(expectedSize);
            this.rbc = rbc;
        }

        public void close() throws IOException {
            rbc.close();
        }

        public boolean isOpen() {
            return rbc.isOpen();
        }

        public int read(ByteBuffer buffer) throws IOException {
            int processedBytes;
            if ((processedBytes = rbc.read(buffer)) > 0) {
                totalBytes += processedBytes;
                logger.progress(toHumanReadableLength(totalBytes) + "/" + formattedSize + " downloaded");
            }
            return processedBytes;
        }

        private String toHumanReadableLength(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return (bytes / 1024) + " KB";
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}
