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

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Protects encryption keys with an AES wrapping key stored in the macOS Keychain.
///
/// The default mode asks the user to approve Keychain access for the current Java process.
/// [UserInteraction#DISABLED] exists for automated tests and forbids authentication UI.
public final class MacOSEncryptionKeyStore implements EncryptionKeyStore {
	public static final String DEFAULT_KEY_NAME = "FabricLoomMicrosoftTokenEncryptionKey";

	private static final Logger LOGGER = LoggerFactory.getLogger(MacOSEncryptionKeyStore.class);
	private static final int WRAPPING_KEY_BYTES = 32;
	private static final int INITIALIZATION_VECTOR_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;
	private static final String SERVICE = "net.fabricmc.fabric-loom.encryption-key";
	private static final String ITEM_DESCRIPTION = "Fabric Loom encryption key";

	private final String keyName;
	private final UserInteraction userInteraction;
	private final SecureRandom secureRandom = new SecureRandom();
	private byte @Nullable [] cachedWrappingKey;

	public MacOSEncryptionKeyStore() {
		this(DEFAULT_KEY_NAME, UserInteraction.REQUIRED);
	}

	public MacOSEncryptionKeyStore(String keyName, UserInteraction userInteraction) {
		this.keyName = Objects.requireNonNull(keyName, "keyName");
		this.userInteraction = Objects.requireNonNull(userInteraction, "userInteraction");

		if (keyName.isBlank()) {
			throw new IllegalArgumentException("keyName must not be blank");
		}
	}

	@Override
	public void prepare() throws LoomNativePlatformException {
		try {
			byte[] wrappingKey = readWrappingKey(true);
			Arrays.fill(wrappingKey, (byte) 0);
		} catch (LoomNativePlatformException e) {
			String message = userInteraction == UserInteraction.REQUIRED
					? "Could not initialize macOS Keychain secure storage. Approve access when prompted to store Microsoft login tokens."
					: "Could not initialize macOS Keychain secure storage.";
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

		byte[] wrappingKey = readWrappingKey(true);

		try {
			byte[] initializationVector = new byte[INITIALIZATION_VECTOR_BYTES];
			secureRandom.nextBytes(initializationVector);
			Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, wrappingKey, initializationVector);
			byte[] ciphertext = cipher.doFinal(encoded);
			byte[] wrapped = Arrays.copyOf(initializationVector, initializationVector.length + ciphertext.length);
			System.arraycopy(ciphertext, 0, wrapped, initializationVector.length, ciphertext.length);
			return new StoredKey(key.getAlgorithm(), wrapped);
		} catch (GeneralSecurityException e) {
			LOGGER.error("Failed to protect an encryption key with the macOS Keychain", e);
			throw new LoomNativePlatformException("Failed to protect an encryption key with the macOS Keychain", e);
		} finally {
			Arrays.fill(wrappingKey, (byte) 0);
		}
	}

	@Override
	public SecretKey read(StoredKey key) throws LoomNativePlatformException {
		Objects.requireNonNull(key, "key");
		byte[] wrapped = key.data();

		if (wrapped.length <= INITIALIZATION_VECTOR_BYTES + GCM_TAG_BITS / Byte.SIZE) {
			throw new LoomNativePlatformException("Invalid wrapped encryption key data");
		}

		byte[] wrappingKey = readWrappingKey(false);

		try {
			byte[] initializationVector = Arrays.copyOf(wrapped, INITIALIZATION_VECTOR_BYTES);
			Cipher cipher = createCipher(Cipher.DECRYPT_MODE, wrappingKey, initializationVector);
			byte[] encoded = cipher.doFinal(wrapped, INITIALIZATION_VECTOR_BYTES, wrapped.length - INITIALIZATION_VECTOR_BYTES);

			try {
				return new SecretKeySpec(encoded, key.algorithm());
			} finally {
				Arrays.fill(encoded, (byte) 0);
			}
		} catch (GeneralSecurityException e) {
			LOGGER.error("Failed to read an encryption key with the macOS Keychain", e);
			throw new LoomNativePlatformException("Failed to read an encryption key with the macOS Keychain", e);
		} finally {
			Arrays.fill(wrappingKey, (byte) 0);
		}
	}

	@Override
	public void delete() throws LoomNativePlatformException {
		try {
			MacOS.delete(SERVICE, keyName, userInteraction);
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to delete the macOS Keychain key {}", keyName, e);
			throw new LoomNativePlatformException("Failed to delete the macOS Keychain key " + keyName, e);
		} finally {
			clearCachedWrappingKey();
		}
	}

	private synchronized byte[] readWrappingKey(boolean create) throws LoomNativePlatformException {
		if (cachedWrappingKey != null) {
			return cachedWrappingKey.clone();
		}

		try {
			byte[] wrappingKey = MacOS.read(SERVICE, keyName, userInteraction);

			if (wrappingKey == null && create) {
				byte[] generated = new byte[WRAPPING_KEY_BYTES];
				secureRandom.nextBytes(generated);

				try {
					MacOS.add(SERVICE, keyName, ITEM_DESCRIPTION, generated, userInteraction);
				} finally {
					Arrays.fill(generated, (byte) 0);
				}

				wrappingKey = MacOS.read(SERVICE, keyName, userInteraction);
			}

			if (wrappingKey == null) {
				throw new LoomNativePlatformException("macOS Keychain key does not exist: " + keyName);
			}

			if (wrappingKey.length != WRAPPING_KEY_BYTES) {
				Arrays.fill(wrappingKey, (byte) 0);
				throw new LoomNativePlatformException("macOS Keychain key has an invalid length: " + keyName);
			}

			cachedWrappingKey = wrappingKey.clone();
			return wrappingKey;
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			throw new LoomNativePlatformException("Failed to access the macOS Keychain key " + keyName, e);
		}
	}

	private synchronized void clearCachedWrappingKey() {
		if (cachedWrappingKey != null) {
			Arrays.fill(cachedWrappingKey, (byte) 0);
			cachedWrappingKey = null;
		}
	}

	private static Cipher createCipher(int mode, byte[] wrappingKey, byte[] initializationVector) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, new SecretKeySpec(wrappingKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, initializationVector));
		return cipher;
	}
}
