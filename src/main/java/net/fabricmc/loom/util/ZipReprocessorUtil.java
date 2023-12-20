/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.intellij.lang.annotations.MagicConstant;

public class ZipReprocessorUtil {
	private ZipReprocessorUtil() { }

	private static final String META_INF = "META-INF/";

	// See https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#signed-jar-file
	private static boolean isSpecialFile(String zipEntryName) {
		if (!zipEntryName.startsWith(META_INF)) {
			return false;
		}

		String[] parts = zipEntryName.split("/");

		if (parts.length != 2) {
			return false;
		}

		return parts[1].startsWith("SIG-")
				|| parts[1].endsWith(".SF")
				|| parts[1].endsWith(".DSA")
				|| parts[1].endsWith(".RSA")
				|| parts[1].endsWith(".EC");
	}

	private static int specialOrdering(String name1, String name2) {
		if (name1.equals(name2)) {
			return 0;
		} else if (name1.equals(Constants.Manifest.PATH)) {
			return -1;
		} else if (name2.equals(Constants.Manifest.PATH)) {
			return 1;
		}

		boolean isName1Special = isSpecialFile(name1);
		boolean isName2Special = isSpecialFile(name2);

		if (isName1Special && isName2Special) {
			return name1.compareTo(name2);
		} else if (isName1Special) {
			return -1;
		} else if (isName2Special) {
			return 1;
		}

		return name1.compareTo(name2);
	}

	public static void reprocessZip(Path file, boolean reproducibleFileOrder, boolean preserveFileTimestamps) throws IOException {
		reprocessZip(file, reproducibleFileOrder, preserveFileTimestamps, ZipEntryCompression.DEFLATED);
	}

	public static void reprocessZip(Path file, boolean reproducibleFileOrder, boolean preserveFileTimestamps, ZipEntryCompression zipEntryCompression) throws IOException {
		if (!reproducibleFileOrder && preserveFileTimestamps) {
			return;
		}

		final Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");

		try (var zipFile = new ZipFile(file.toFile());
				var fileOutputStream = Files.newOutputStream(tempFile)) {
			ZipEntry[] entries;

			if (reproducibleFileOrder) {
				entries = zipFile.stream()
						.sorted(Comparator.comparing(ZipEntry::getName, ZipReprocessorUtil::specialOrdering))
						.toArray(ZipEntry[]::new);
			} else {
				entries = zipFile.stream()
						.toArray(ZipEntry[]::new);
			}

			try (var zipOutputStream = new ZipOutputStream(fileOutputStream)) {
				zipOutputStream.setMethod(zipOutputStreamCompressionMethod(zipEntryCompression));

				for (ZipEntry entry : entries) {
					ZipEntry newEntry = entry;

					if (!preserveFileTimestamps) {
						newEntry = new ZipEntry(entry.getName());
						setConstantFileTime(newEntry);
					}

					newEntry.setMethod(zipEntryCompressionMethod(zipEntryCompression));
					copyZipEntry(zipOutputStream, newEntry, zipFile.getInputStream(entry));
				}
			}
		}

		Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Appends an entry to a zip file, persevering the existing entry order and time stamps.
	 * The new entry is added with a constant time stamp to ensure reproducibility.
	 * This method should only be used when a reproducible output is required, use {@link ZipUtils#add(Path, String, byte[])} normally.
	 */
	public static void appendZipEntry(Path file, String path, byte[] data) throws IOException {
		final Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");

		try (var zipFile = new ZipFile(file.toFile());
				var fileOutputStream = Files.newOutputStream(tempFile)) {
			ZipEntry[] entries = zipFile.stream().toArray(ZipEntry[]::new);

			try (var zipOutputStream = new ZipOutputStream(fileOutputStream)) {
				// Copy existing entries
				for (ZipEntry entry : entries) {
					if (entry.getName().equals(path)) {
						throw new IllegalArgumentException("Zip file (%s) already contains entry (%s)".formatted(file.getFileName().toString(), path));
					}

					copyZipEntry(zipOutputStream, entry, zipFile.getInputStream(entry));
				}

				// Append the new entry
				var entry = new ZipEntry(path);
				setConstantFileTime(entry);
				zipOutputStream.putNextEntry(entry);
				zipOutputStream.write(data, 0, data.length);
				zipOutputStream.closeEntry();
			}
		}

		Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void copyZipEntry(ZipOutputStream zipOutputStream, ZipEntry entry, InputStream inputStream) throws IOException {
		zipOutputStream.putNextEntry(entry);
		byte[] buf = new byte[1024];
		int length;

		while ((length = inputStream.read(buf)) > 0) {
			zipOutputStream.write(buf, 0, length);
		}

		zipOutputStream.closeEntry();
	}

	private static void setConstantFileTime(ZipEntry entry) {
		// See https://github.com/openjdk/jdk/blob/master/test/jdk/java/util/zip/ZipFile/ZipEntryTimeBounds.java
		entry.setTime(new GregorianCalendar(1980, Calendar.JANUARY, 1, 0, 0, 0).getTimeInMillis());
	}

	@MagicConstant(valuesFromClass = ZipOutputStream.class)
	private static int zipOutputStreamCompressionMethod(ZipEntryCompression compression) {
		return switch (compression) {
		case STORED -> ZipOutputStream.STORED;
		case DEFLATED -> ZipOutputStream.DEFLATED;
		};
	}

	@MagicConstant(valuesFromClass = ZipEntry.class)
	private static int zipEntryCompressionMethod(ZipEntryCompression compression) {
		return switch (compression) {
		case STORED -> ZipEntry.STORED;
		case DEFLATED -> ZipEntry.DEFLATED;
		};
	}
}
