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

import org.gradle.api.specs.Spec;
import org.gradle.internal.logging.progress.ProgressLogger;

import java.io.File;
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
public interface DownloadSpec {

    /**
     * Spec to validate the already existing file this DownloadSpec will download.
     * If this spec returns true, its considered up-to-date, as long as eTag & onlyIfModified
     * checks pass, the file will not be re-downloaded.
     *
     * Basically, using this allows you to determine externally if the file is corrupt
     * and force a re-download at execution time instead of pre maturely.
     *
     * @param spec The Spec.
     */
    void fileUpToDateWhen(Spec<File> spec);

    /**
     * Gets the source URL for the file that will be downloaded.
     *
     * @return The URL.
     */
    URL getSrc();

    /**
     * Gets the destination file to download to.
     *
     * @return The file.
     */
    File getDest();

    /**
     * @return If this DownloadSpec checks for onlyIfModified HTTP headers.
     */
    boolean getOnlyIfModified();

    /**
     * @return The ETag validation method used by this DownloadSpec.
     */
    DownloadAction.UseETag getUseETag();

    /**
     * The file to store the ETag in.
     * Defaults to getDest + .etag
     *
     * @return The file to store the ETag in.
     */
    File getETagFile();

    /**
     * Gets the User-Agent header this DownloadSpec will use for requests.
     *
     * @return
     */
    String getUserAgent();

    /**
     * If this DownloadSpec is being quiet.
     * Silences a few log messages and disables progress logging.
     *
     * @return If silent is enabled.
     */
    boolean isQuiet();

    /**
     * To be used after the DownloadSpec has been executed,
     * will return weather the DownloadSpec was up-to-date and
     * no work was performed.
     *
     * @return If the DownloadSpec was up-to-date.
     */
    boolean isUpToDate();

    /**
     * Sets the source URL to download.
     *
     * @param src The source.
     */
    void setSrc(Object src);

    /**
     * Sets the destination to store the downloaded file.
     *
     * @param dest The destination.
     */
    void setDest(Object dest);

    /**
     * Sets weather this DownloadSpec should obey onlyIfModified HTTP headers.
     *
     * @param onlyIfModified Weather to use onlyIfModified.
     */
    void setOnlyIfModified(boolean onlyIfModified);

    /**
     * Sets weather this DownloadSpec should use ETags for HTTP requests.
     *
     * @param useETag Weather to use ETags.
     */
    void setUseETag(Object useETag);

    /**
     * Sets the file to store the ETag in.
     * This defaults to getDest + .etag
     *
     * @param eTagFile The file to store the ETag in.
     */
    void setETagFile(Object eTagFile);

    /**
     * Sets the User-Agent HTTP header string to use for HTTP requests.
     *
     * @param userAgent The User-Agent string.
     */
    void setUserAgent(String userAgent);

    /**
     * Sets weather quiet is enabled or not.
     * Disables some log messages and progress logging.
     *
     * @param quiet If quiet is enabled.
     */
    void setQuiet(boolean quiet);

    /**
     * Forcibly set the ProgressLogger to use for this DownloadSpec.
     * This bypasses quiet checks.
     *
     * @param logger The ProgressLogger.
     */
    void setProgressLogger(ProgressLogger logger);

}
