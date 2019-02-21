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

import com.google.common.io.Files;
import net.fabricmc.loom.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;

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
public class DownloadAction implements DownloadSpec {

    private final Project project;
    private Object src;
    private Object dest;
    private boolean onlyIfModified;
    private UseETag useETag = UseETag.FALSE;
    private Object eTagFile;
    private String userAgent;
    private boolean quiet;
    private AndSpec<File> fileUpToDate = AndSpec.empty();

    private ProgressLogger progressLogger;

    private boolean upToDate;

    public DownloadAction(Project project) {
        this.project = project;
    }

    public void execute() throws IOException {
        if (src == null) {
            throw new IllegalArgumentException("Download source not provided");
        }
        if (dest == null) {
            throw new IllegalArgumentException("Download destination not provided.");
        }

        if (progressLogger == null && !isQuiet()) {
            ServiceRegistry registry = ((ProjectInternal) project).getServices();
            ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
            progressLogger = factory.newOperation(getClass()).setDescription("Download " + src);
        }

        URL src = getSrc();
        File dest = getDest();

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet request = new HttpGet(src.toString());
            long timestamp = 0;
            if (dest.exists()) {
                timestamp = dest.lastModified();
            }
            if (onlyIfModified && dest.exists()) {
                request.addHeader("If-Modified-Since", DateUtils.formatDate(new Date(timestamp)));
            }
            if (getUseETag().isEnabled()) {
                String etag = loadETag(src);
                if (!getUseETag().weak && StringUtils.startsWith(etag, "W/")) {
                    etag = null;
                }
                if (etag != null) {
                    request.addHeader("If-None-Match", etag);
                }
            }
            request.addHeader("Accept-Encoding", "gzip");
            if (getUserAgent() != null) {
                request.addHeader("User-Agent", getUserAgent());
            }

            try (CloseableHttpResponse response = client.execute(request)) {
                int code = response.getStatusLine().getStatusCode();
                if ((code < 200 || code > 299) && code != HttpStatus.SC_NOT_MODIFIED) {
                    throw new ClientProtocolException(response.getStatusLine().getReasonPhrase());
                }
                long lastModified = 0;
                Header lastModifiedHeader = response.getLastHeader("Last-Modified");
                if (lastModifiedHeader != null) {
                    String val = lastModifiedHeader.getValue();
                    if (!StringUtils.isEmpty(val)) {
                        Date date = DateUtils.parseDate(val);
                        if (date != null) {
                            lastModified = date.getTime();
                        }
                    }
                }
                if ((code == HttpStatus.SC_NOT_MODIFIED || (lastModified != 0 && timestamp >= lastModified)) && fileUpToDate.isSatisfiedBy(dest)) {
                    if (!quiet) {
                        project.getLogger().info("Not Modified. Skipping '{}'.", src);
                    }
                    upToDate = true;
                    return;
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return;//kden..
                }

                String humanSize = "";
                long contentLen = entity.getContentLength();
                if (contentLen >= 0) {
                    humanSize = toLengthText(contentLen);
                }
                long processed = 0;
                if (progressLogger != null) {
                    progressLogger.started();
                }
                boolean finished = false;
                try (InputStream is = entity.getContent()) {
                    try (FileOutputStream fos = new FileOutputStream(Utils.makeFile(dest))) {
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = is.read(buffer)) >= 0) {
                            fos.write(buffer, 0, len);
                            processed += len;
                            if (progressLogger != null) {
                                progressLogger.progress(toLengthText(processed) + "/" + humanSize + " downloaded");
                            }
                        }
                        fos.flush();
                        finished = true;
                    }
                } finally {
                    if (!finished) {
                        dest.delete();
                    }
                    if (progressLogger != null) {
                        progressLogger.completed();
                    }
                }
                if (onlyIfModified && lastModified > 0) {
                    dest.setLastModified(lastModified);
                }
                if (getUseETag().isEnabled()) {
                    Header eTagHeader = response.getFirstHeader("ETag");
                    if (eTagHeader != null) {
                        String etag = eTagHeader.getValue();
                        boolean isWeak = StringUtils.startsWith(etag, "W/");
                        if (isWeak && getUseETag().warnOnWeak && !quiet) {
                            project.getLogger().warn("Weak ETag found.");
                        }
                        if (!isWeak || getUseETag().weak) {
                            saveETag(src, etag);
                        }
                    }
                }
            }
        }
    }

    protected String loadETag(URL url) {
        File eTagFile = getETagFile();
        if (!eTagFile.exists()) {
            return null;
        }
        try {
            return Files.asCharSource(eTagFile, UTF_8).read();
        } catch (IOException e) {
            project.getLogger().warn("Error reading ETag file '{}'.", eTagFile);
            return null;
        }
    }

    protected void saveETag(URL url, String eTag) {
        File eTagFile = getETagFile();
        try {
            Files.asCharSink(Utils.makeFile(eTagFile), UTF_8).write(eTag);
        } catch (IOException e) {
            project.getLogger().warn("Error saving ETag file '{}'.", eTagFile, e);
        }
    }

    private String toLengthText(long bytes) {
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

    @Override
    public void fileUpToDateWhen(Spec<File> spec) {
        fileUpToDate = fileUpToDate.and(spec);
    }

    //@formatter:off
    @Override public URL getSrc() { return Utils.resolveURL(src); }
    @Override public File getDest() { return project.file(dest); }
    @Override public boolean getOnlyIfModified() { return onlyIfModified; }
    @Override public UseETag getUseETag() { return useETag; }
    @Override public File getETagFile() { return getETagFile_(); }
    @Override public String getUserAgent() { return userAgent; }
    @Override public boolean isQuiet() { return quiet; }
    @Override public boolean isUpToDate() { return upToDate; }
    @Override public void setSrc(Object src) { this.src = src; }
    @Override public void setDest(Object dest) { this.dest = dest; }
    @Override public void setOnlyIfModified(boolean onlyIfModified) { this.onlyIfModified = onlyIfModified; }
    @Override public void setUseETag(Object eTag) { this.useETag = UseETag.parse(eTag); }
    @Override public void setETagFile(Object eTagFile) { this.eTagFile = eTagFile; }
    @Override public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    @Override public void setQuiet(boolean quiet) { this.quiet = quiet; }
    @Override public void setProgressLogger(ProgressLogger progressLogger) { this.progressLogger = progressLogger; }
    //@formatter:on

    private File getETagFile_() {
        if (eTagFile == null) {
            File dest = getDest();
            return new File(dest.getAbsoluteFile().getParentFile(), dest.getName() + ".etag");
        }
        return project.file(eTagFile);
    }

    public enum UseETag {
        FALSE(false, false),
        TRUE(true, true),
        ALL(true, false),
        STRONG(false, false);

        public final boolean weak;
        public final boolean warnOnWeak;

        UseETag(boolean weak, boolean warnOnWeak) {
            this.weak = weak;
            this.warnOnWeak = warnOnWeak;
        }

        public boolean isEnabled() {
            return this != FALSE;
        }

        public static UseETag parse(Object value) {
            if (value instanceof UseETag) {
                return (UseETag) value;
            } else if (value instanceof Boolean) {
                if ((Boolean) value) {
                    return TRUE;
                } else {
                    return FALSE;
                }
            } else if (value instanceof String) {
                switch ((String) value) {
                    case "true":
                        return TRUE;
                    case "false":
                        return FALSE;
                    case "all":
                        return ALL;
                    case "strong":
                        return STRONG;
                }
            }
            throw new IllegalArgumentException("Unable to parse ETag, Unknown value: " + value.toString());
        }
    }

}
