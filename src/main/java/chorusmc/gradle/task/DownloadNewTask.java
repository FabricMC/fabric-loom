package chorusmc.gradle.task;

import chorusmc.gradle.util.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import chorusmc.gradle.util.progress.ProgressLogger;

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
