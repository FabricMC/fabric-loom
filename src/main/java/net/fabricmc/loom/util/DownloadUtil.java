/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Chocohead
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
package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;

import com.google.common.io.Files;

public class DownloadUtil {
	public static void downloadIfChanged(URL from, File to, Logger logger) throws IOException {
		downloadIfChanged(from, to, logger, false);
	}

	public static void downloadIfChanged(URL from, File to, Logger logger, boolean quiet) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) from.openConnection();

		//If the output already exists we'll use it's last modified time
		if (to.exists()) connection.setIfModifiedSince(to.lastModified());

		//Try use the ETag if there's one for the file we're downloading
		String etag = loadETag(from, to, logger);
		if (etag != null) connection.setRequestProperty("If-None-Match", etag);

		//We want to download gzip compressed stuff
		connection.setRequestProperty("Accept-Encoding", "gzip");

		//We shouldn't need to set a user agent, but it's here just in case
		//connection.setRequestProperty("User-Agent", null);

		//Try make the connection, it will hang here if the connection is bad
		connection.connect();

		int code = connection.getResponseCode();
		if ((code < 200 || code > 299) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//Didn't get what we expected
			throw new IOException(connection.getResponseMessage());
		}

		long modifyTime = connection.getHeaderFieldDate("Last-Modified", -1);
		if (code == HttpURLConnection.HTTP_NOT_MODIFIED || modifyTime > 0 && to.exists() && to.lastModified() >= modifyTime) {
			if (!quiet) logger.info("'{}' Not Modified, skipping.", to);
			return; //What we've got is already fine
		}

		long contentLength = connection.getContentLengthLong();
		if (!quiet && contentLength >= 0) logger.info("'{}' Changed, downloading {}", to, toLengthText(contentLength));

		try {//Try download to the output
			FileUtils.copyInputStreamToFile(connection.getInputStream(), to);
		} catch (IOException e) {
			to.delete(); //Probably isn't good if it fails to copy/save
			throw e;
		}

		//Set the modify time to match the server's (if we know it)
		if (modifyTime > 0) to.setLastModified(modifyTime);

		//Save the ETag (if we know it)
		String eTag = connection.getHeaderField("ETag");
		if (eTag != null) {
			//Log if we get a weak ETag and we're not on quiet
			if (!quiet && eTag.startsWith("W/")) logger.warn("Weak ETag found.");

			saveETag(to, eTag, logger);
		}
	}

	private static File getETagFile(File file) {
		return new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".etag");
	}

	private static String loadETag(URL from, File to, Logger logger) {
		File eTagFile = getETagFile(to);
		if (!eTagFile.exists()) return null;

		try {
			return Files.asCharSource(eTagFile, StandardCharsets.UTF_8).read();
		} catch (IOException e) {
			logger.warn("Error reading ETag file '{}'.", eTagFile);
			return null;
		}
	}

	private static void saveETag(File to, String eTag, Logger logger) {
		File eTagFile = getETagFile(to);
		try {
			if (!eTagFile.exists()) eTagFile.createNewFile();
			Files.asCharSink(eTagFile, StandardCharsets.UTF_8).write(eTag);
		} catch (IOException e) {
			logger.warn("Error saving ETag file '{}'.", eTagFile, e);
		}
	}

	private static String toLengthText(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return bytes / 1024 + " KB";
		} else if (bytes < 1024 * 1024 * 1024) {
			return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		} else {
			return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
		}
	}
}