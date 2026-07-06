/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.util.nativeplatform;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import net.fabricmc.loom.util.Platform;

public final class LoomNativePlatform {
	private static final NativePlatform PLATFORM = create();

	private LoomNativePlatform() {
	}

	public static List<ProcessHandle> getProcessesWithLockOn(Path path) throws LoomNativePlatformException {
		return PLATFORM.getProcessesWithLockOn(path);
	}

	public static List<String> getWindowTitlesForPid(long pid) throws LoomNativePlatformException {
		return PLATFORM.getWindowTitlesForPid(pid);
	}

	public static boolean isSupported() {
		return PLATFORM != UnsupportedNativePlatform.INSTANCE;
	}

	private static NativePlatform create() {
		if (Platform.CURRENT.getOperatingSystem().isWindows()) {
			return WindowsNativePlatform.create();
		}

		return UnsupportedNativePlatform.INSTANCE;
	}

	interface NativePlatform {
		List<ProcessHandle> getProcessesWithLockOn(Path path) throws LoomNativePlatformException;

		List<String> getWindowTitlesForPid(long pid) throws LoomNativePlatformException;
	}

	private enum UnsupportedNativePlatform implements NativePlatform {
		INSTANCE;

		@Override
		public List<ProcessHandle> getProcessesWithLockOn(Path path) {
			return Collections.emptyList();
		}

		@Override
		public List<String> getWindowTitlesForPid(long pid) {
			return Collections.emptyList();
		}
	}
}
