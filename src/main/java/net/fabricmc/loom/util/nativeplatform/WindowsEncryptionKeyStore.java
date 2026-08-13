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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Protects encryption keys with a persisted Windows CNG RSA key.
///
/// The default mode stores the RSA key in the Microsoft Platform Crypto Provider and configures
/// Windows to request user consent for private-key operations. [UserInteraction#DISABLED] exists
/// for automated tests: it uses the Microsoft Software Key Storage Provider and forbids UI.
public final class WindowsEncryptionKeyStore implements EncryptionKeyStore {
	public static final String DEFAULT_KEY_NAME = "FabricLoomMicrosoftTokenEncryptionKey";

	private static final Logger LOGGER = LoggerFactory.getLogger(WindowsEncryptionKeyStore.class);
	private static final int RSA_KEY_BITS = 2048;

	private final String keyName;
	private final UserInteraction userInteraction;

	public WindowsEncryptionKeyStore() {
		this(DEFAULT_KEY_NAME, UserInteraction.REQUIRED);
	}

	public WindowsEncryptionKeyStore(String keyName, UserInteraction userInteraction) {
		this.keyName = Objects.requireNonNull(keyName, "keyName");
		this.userInteraction = Objects.requireNonNull(userInteraction, "userInteraction");

		if (keyName.isBlank()) {
			throw new IllegalArgumentException("keyName must not be blank");
		}
	}

	@Override
	public void prepare() throws LoomNativePlatformException {
		try (Arena arena = Arena.ofConfined(); NativeHandle provider = openProvider(arena); NativeHandle wrappingKey = openOrCreateKey(arena, provider)) {
			byte[] plaintext = new byte[] {0};
			byte[] encrypted = Win32.ncryptEncrypt(arena, wrappingKey.segment(), plaintext);
			byte[] decrypted = Win32.ncryptDecrypt(arena, wrappingKey.segment(), encrypted, nativeFlags());

			if (!Arrays.equals(plaintext, decrypted)) {
				throw new LoomNativePlatformException("Windows CNG key validation failed");
			}
		} catch (Throwable e) {
			String message = userInteraction == UserInteraction.REQUIRED
					? "Could not initialize TPM-backed secure storage. Microsoft login requires an enabled and provisioned TPM on Windows, and setup must be approved when prompted."
					: "Could not initialize Windows CNG secure storage.";
			throw new LoomNativePlatformException(message, e);
		}
	}

	@Override
	public StoredKey store(SecretKey key) throws LoomNativePlatformException {
		Objects.requireNonNull(key, "key");
		byte[] encoded = key.getEncoded();

		if (encoded == null || encoded.length == 0) {
			throw new IllegalArgumentException("key must be encodable");
		}

		try (Arena arena = Arena.ofConfined(); NativeHandle provider = openProvider(arena); NativeHandle wrappingKey = openOrCreateKey(arena, provider)) {
			return new StoredKey(key.getAlgorithm(), Win32.ncryptEncrypt(arena, wrappingKey.segment(), encoded));
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to protect an encryption key with Windows CNG", e);
			throw new LoomNativePlatformException("Failed to protect an encryption key with Windows CNG", e);
		}
	}

	@Override
	public SecretKey read(StoredKey key) throws LoomNativePlatformException {
		Objects.requireNonNull(key, "key");
		byte[] encrypted = key.data();

		try (Arena arena = Arena.ofConfined(); NativeHandle provider = openProvider(arena); NativeHandle wrappingKey = openExistingKey(arena, provider)) {
			byte[] encoded = Win32.ncryptDecrypt(arena, wrappingKey.segment(), encrypted, nativeFlags());
			return new SecretKeySpec(encoded, key.algorithm());
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to read an encryption key with Windows CNG", e);
			throw new LoomNativePlatformException("Failed to read an encryption key with Windows CNG", e);
		}
	}

	@Override
	public void delete() throws LoomNativePlatformException {
		try (Arena arena = Arena.ofConfined(); NativeHandle provider = openProvider(arena)) {
			MemorySegment key = Win32.ncryptOpenKey(arena, provider.segment(), keyName, nativeFlags());

			if (key.equals(MemorySegment.NULL)) {
				return;
			}

			try (NativeHandle keyHandle = new NativeHandle(key)) {
				Win32.ncryptDeleteKey(keyHandle.segment(), nativeFlags());
				keyHandle.release();
			}
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to delete the Windows CNG key {}", keyName, e);
			throw new LoomNativePlatformException("Failed to delete the Windows CNG key " + keyName, e);
		}
	}

	private NativeHandle openProvider(Arena arena) throws Throwable {
		String provider = userInteraction == UserInteraction.REQUIRED
				? Win32.MS_PLATFORM_CRYPTO_PROVIDER
				: Win32.MS_KEY_STORAGE_PROVIDER;
		return new NativeHandle(Win32.ncryptOpenStorageProvider(arena, provider, 0));
	}

	private NativeHandle openExistingKey(Arena arena, NativeHandle provider) throws Throwable {
		MemorySegment key = Win32.ncryptOpenKey(arena, provider.segment(), keyName, nativeFlags());

		if (key.equals(MemorySegment.NULL)) {
			throw new LoomNativePlatformException("Windows CNG key does not exist: " + keyName);
		}

		return configureOpenedKey(arena, key);
	}

	private NativeHandle openOrCreateKey(Arena arena, NativeHandle provider) throws Throwable {
		MemorySegment existing = Win32.ncryptOpenKey(arena, provider.segment(), keyName, nativeFlags());

		if (!existing.equals(MemorySegment.NULL)) {
			return configureOpenedKey(arena, existing);
		}

		MemorySegment created = Win32.ncryptCreatePersistedKey(arena, provider.segment(), keyName, 0);

		if (created.equals(MemorySegment.NULL)) {
			return openExistingKey(arena, provider);
		}

		NativeHandle key = new NativeHandle(created);

		try {
			setWindowHandle(arena, key.segment());
			Win32.ncryptSetDwordProperty(arena, key.segment(), Win32.NCRYPT_LENGTH_PROPERTY, RSA_KEY_BITS);
			Win32.ncryptSetDwordProperty(arena, key.segment(), Win32.NCRYPT_KEY_USAGE_PROPERTY, Win32.NCRYPT_ALLOW_DECRYPT_FLAG);

			if (userInteraction == UserInteraction.REQUIRED) {
				Win32.ncryptSetUiPolicy(arena, key.segment(),
						"Set up Fabric Loom secure token storage",
						"Fabric Loom Microsoft login token key",
						"Fabric Loom uses this key to securely store and access your Microsoft login tokens.");
			}

			Win32.ncryptFinalizeKey(key.segment(), nativeFlags());
			return key;
		} catch (Throwable e) {
			discardCreatedKey(key, e);
			throw e;
		}
	}

	private NativeHandle configureOpenedKey(Arena arena, MemorySegment key) throws Throwable {
		NativeHandle keyHandle = new NativeHandle(key);

		try {
			setWindowHandle(arena, key);
			return keyHandle;
		} catch (Throwable e) {
			try {
				keyHandle.close();
			} catch (Throwable closeException) {
				e.addSuppressed(closeException);
			}

			throw e;
		}
	}

	private void setWindowHandle(Arena arena, MemorySegment key) throws Throwable {
		if (userInteraction != UserInteraction.REQUIRED) {
			return;
		}

		MemorySegment window = Win32.getForegroundWindow();

		if (!window.equals(MemorySegment.NULL)) {
			Win32.ncryptSetWindowHandle(arena, key, window);
		}
	}

	private void discardCreatedKey(NativeHandle key, Throwable originalException) {
		try {
			Win32.ncryptDeleteKey(key.segment(), nativeFlags());
			key.release();
		} catch (Throwable deleteException) {
			originalException.addSuppressed(deleteException);

			try {
				key.close();
			} catch (Throwable closeException) {
				originalException.addSuppressed(closeException);
			}
		}
	}

	private int nativeFlags() {
		return userInteraction == UserInteraction.DISABLED ? Win32.NCRYPT_SILENT_FLAG : 0;
	}

	private static final class NativeHandle implements AutoCloseable {
		private MemorySegment segment;

		private NativeHandle(MemorySegment segment) {
			this.segment = segment;
		}

		private MemorySegment segment() {
			return segment;
		}

		private void release() {
			segment = MemorySegment.NULL;
		}

		@Override
		public void close() throws LoomNativePlatformException {
			if (!segment.equals(MemorySegment.NULL)) {
				try {
					Win32.ncryptFreeObject(segment);
				} catch (LoomNativePlatformException e) {
					throw e;
				} catch (Throwable e) {
					throw new LoomNativePlatformException("NCryptFreeObject failed", e);
				} finally {
					segment = MemorySegment.NULL;
				}
			}
		}
	}
}
