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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipReprocessorUtil {
	/**
	 * See {@link org.gradle.api.internal.file.archive.ZipCopyAction} about this.
	 */
	private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

	private ZipReprocessorUtil() { }

	private static final String MANIFEST_LOCATION = "META-INF/MANIFEST.MF";
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
		} else if (name1.equals(MANIFEST_LOCATION)) {
			return -1;
		} else if (name2.equals(MANIFEST_LOCATION)) {
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

	public static void reprocessZip(File file, boolean reproducibleFileOrder, boolean preserveFileTimestamps) throws IOException {
		if (!reproducibleFileOrder && preserveFileTimestamps) {
			return;
		}

		try (var zipFile = new ZipFile(file)) {
			ZipEntry[] entries;

			if (reproducibleFileOrder) {
				entries = zipFile.stream()
						.sorted(Comparator.comparing(ZipEntry::getName, ZipReprocessorUtil::specialOrdering))
						.toArray(ZipEntry[]::new);
			} else {
				entries = zipFile.stream()
						.toArray(ZipEntry[]::new);
			}

			final var outZip = new ByteArrayOutputStream(entries.length);

			try (var zipOutputStream = new ZipOutputStream(outZip)) {
				for (ZipEntry entry : entries) {
					ZipEntry newEntry = entry;

					if (!preserveFileTimestamps) {
						newEntry = new ZipEntry(entry.getName());
						setConstantFileTime(newEntry);
					}

					copyZipEntry(zipOutputStream, newEntry, zipFile.getInputStream(entry));
				}
			}

			try (var fileOutputStream = new FileOutputStream(file)) {
				outZip.writeTo(fileOutputStream);
			}
		}
	}

	/**
	 * Appends an entry to a zip file, persevering the existing entry order and time stamps.
	 * The new entry is added with a constant time stamp to ensure reproducibility.
	 * This method should only be used when a reproducible output is required, use {@link ZipUtils#add(Path, String, byte[])} normally.
	 */
	public static void appendZipEntry(File file, String path, byte[] data) throws IOException {
		try (var zipFile = new ZipFile(file)) {
			ZipEntry[] entries = zipFile.stream().toArray(ZipEntry[]::new);

			final var outZip = new ByteArrayOutputStream(entries.length);

			try (var zipOutputStream = new ZipOutputStream(outZip)) {
				// Copy existing entries
				for (ZipEntry entry : entries) {
					copyZipEntry(zipOutputStream, entry, zipFile.getInputStream(entry));
				}

				// Append the new entry
				var entry = new ZipEntry(path);
				setConstantFileTime(entry);
				zipOutputStream.putNextEntry(entry);
				zipOutputStream.write(data, 0, data.length);
				zipOutputStream.closeEntry();
			}

			try (var fileOutputStream = new FileOutputStream(file)) {
				outZip.writeTo(fileOutputStream);
			}
		}
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
		entry.setTime(ZipReprocessorUtil.CONSTANT_TIME_FOR_ZIP_ENTRIES);
		entry.setLastModifiedTime(FileTime.fromMillis(ZipReprocessorUtil.CONSTANT_TIME_FOR_ZIP_ENTRIES));
		entry.setLastAccessTime(FileTime.fromMillis(ZipReprocessorUtil.CONSTANT_TIME_FOR_ZIP_ENTRIES));
	}
}
