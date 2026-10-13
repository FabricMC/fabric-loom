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

import org.jspecify.annotations.Nullable;

public class LoomNativePlatformException extends Exception {
	public LoomNativePlatformException(String message) {
		super(message);
	}

	public LoomNativePlatformException(String message, Throwable cause) {
		super(message, cause);
	}

	public static LoomNativePlatformException fromWin32Error(String operation, int errorCode) {
		return new LoomNativePlatformException(formatWin32Error(operation, errorCode));
	}

	public static String formatWin32Error(String operation, int errorCode) {
		final StringBuilder message = new StringBuilder("%s failed: Windows error %d (0x%08X)".formatted(operation, errorCode, errorCode));
		final String windowsMessage = tryFormatMessage(errorCode);

		if (windowsMessage != null && !windowsMessage.isBlank()) {
			message.append(": ").append(windowsMessage);
		}

		return message.toString();
	}

	private static @Nullable String tryFormatMessage(int errorCode) {
		try {
			return Win32.tryFormatMessage(errorCode);
		} catch (Throwable e) {
			return null;
		}
	}
}
