/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2017 FabricMC
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
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reference counted fs handling.
 *
 * <p>The implementation closes file systems opened by itself once they are closed as often as they were opened. This
 * allows intersecting open+close actions on e.g. the zip based file systems. The caller has to ensure open and close
 * invocations are mirrored.
 */
public final class FileSystemHandler {
	public static FileSystem open(URI uri, Map<String, ?> env) throws IOException {
		synchronized (fsRefs) {
			boolean opened = false;
			FileSystem ret;

			try {
				ret = FileSystems.getFileSystem(uri);
			} catch (FileSystemNotFoundException e) {
				try {
					ret = FileSystems.newFileSystem(uri, env);
					opened = true;
				} catch (FileSystemAlreadyExistsException f) {
					ret = FileSystems.getFileSystem(uri);
				}
			}

			Integer count = fsRefs.get(ret);

			if (count == null || count == 0) {
				count = opened ? 1 : -1;
			} else if (opened) {
				throw new IllegalStateException("fs ref tracking indicates fs " + ret + " is open, but it wasn't");
			} else {
				count += Integer.signum(count);
			}

			fsRefs.put(ret, count);

			return ret;
		}
	}

	public static void close(FileSystem fs) throws IOException {
		synchronized (fsRefs) {
			Integer count = fsRefs.get(fs);

			if (count == null || count == 0) {
				throw new IllegalStateException("fs " + fs + " never opened via FileSystemHandler");
			}

			boolean canClose = count > 0;
			count -= Integer.signum(count);

			if (count == 0) {
				fsRefs.remove(fs);
				if (canClose) fs.close();
			} else {
				fsRefs.put(fs, count);
			}
		}
	}

	public static final Map<FileSystem, Integer> fsRefs; // fs->refCount map, counting positive if the fs was originally opened by this system

	static {
		synchronized (FileSystems.class) {
			final String property = "fsRefsProvider";
			String provider = System.getProperty(property);

			if (provider != null) {
				try {
					int pos = provider.lastIndexOf('/');
					Class<?> providerClass = Class.forName(provider.substring(0, pos));
					Field field = providerClass.getField(provider.substring(pos + 1));
					@SuppressWarnings("unchecked")
					Map<FileSystem, Integer> map = (Map<FileSystem, Integer>) field.get(null);
					fsRefs = map;
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			} else {
				fsRefs = new IdentityHashMap<>();
				provider = String.format("%s/%s", FileSystemHandler.class.getName(), "fsRefs");
				System.setProperty(property, provider);
			}
		}
	}
}
