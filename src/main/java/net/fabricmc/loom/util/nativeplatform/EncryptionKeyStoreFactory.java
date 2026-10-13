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
import java.util.Locale;
import java.util.Objects;

import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Platform;

public final class EncryptionKeyStoreFactory {
	private static final String KEY_NAME_PREFIX = "FabricLoomMicrosoftTokenEncryptionKey-";

	private EncryptionKeyStoreFactory() {
	}

	public static EncryptionKeyStore create(Path encryptedFile) {
		return create(keyNameFor(encryptedFile), EncryptionKeyStore.UserInteraction.REQUIRED);
	}

	public static EncryptionKeyStore create(String keyName, EncryptionKeyStore.UserInteraction userInteraction) {
		Objects.requireNonNull(keyName, "keyName");
		Objects.requireNonNull(userInteraction, "userInteraction");

		return switch (Platform.CURRENT.getOperatingSystem()) {
		case WINDOWS -> new WindowsEncryptionKeyStore(keyName, userInteraction);
		case MAC_OS -> new MacOSEncryptionKeyStore(keyName, userInteraction);
		case LINUX -> EncryptionKeyStore.FALLBACK;
		};
	}

	/// Ensures that different Gradle home dirs use a different key.
	public static String keyNameFor(Path encryptedFile) {
		Objects.requireNonNull(encryptedFile, "encryptedFile");
		String normalizedPath = encryptedFile.toAbsolutePath().normalize().toString();

		if (Platform.CURRENT.getOperatingSystem().isWindows()) {
			normalizedPath = normalizedPath.toLowerCase(Locale.ROOT);
		}

		return KEY_NAME_PREFIX + Checksum.of(normalizedPath).sha256().hex();
	}
}
