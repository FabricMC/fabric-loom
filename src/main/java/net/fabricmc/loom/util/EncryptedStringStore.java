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

package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore;
import net.fabricmc.loom.util.nativeplatform.LoomNativePlatformException;

/// Reads and writes a UTF-8 string encrypted with a platform-protected Java encryption key.
///
/// Encryption uses the standard Java Cryptography implementation of AES-256-GCM.
public final class EncryptedStringStore {
	private static final int VERSION = 1;
	private static final int AES_KEY_BITS = 256;
	private static final int GCM_TAG_BITS = 128;
	private static final int INITIALIZATION_VECTOR_BYTES = 12;
	private static final byte[] AAD = "fabric-loom-encrypted-string-v1".getBytes(StandardCharsets.UTF_8);
	private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
	private static final Gson GSON = new Gson();

	private final EncryptionKeyStore keyStore;
	private final SecureRandom secureRandom;

	public EncryptedStringStore(EncryptionKeyStore keyStore) {
		this(keyStore, new SecureRandom());
	}

	EncryptedStringStore(EncryptionKeyStore keyStore, SecureRandom secureRandom) {
		this.keyStore = Objects.requireNonNull(keyStore, "keyStore");
		this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
	}

	public void write(Path path, String value) throws IOException, LoomNativePlatformException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(value, "value");
		byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);

		try {
			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(AES_KEY_BITS, secureRandom);
			SecretKey key = generator.generateKey();
			EncryptionKeyStore.StoredKey storedKey = keyStore.store(key);

			if (!"AES".equalsIgnoreCase(storedKey.algorithm())) {
				throw new IOException("Encryption key store returned an unsupported algorithm: " + storedKey.algorithm());
			}

			byte[] initializationVector = new byte[INITIALIZATION_VECTOR_BYTES];
			secureRandom.nextBytes(initializationVector);
			Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, key, initializationVector);
			byte[] ciphertext = cipher.doFinal(plaintext);
			Base64.Encoder encoder = Base64.getEncoder();
			EncryptedValue encrypted = new EncryptedValue(
					VERSION,
					encoder.encodeToString(storedKey.data()),
					encoder.encodeToString(initializationVector),
					encoder.encodeToString(ciphertext)
			);
			writeFile(path, GSON.toJson(encrypted));
		} catch (GeneralSecurityException e) {
			throw new IOException("Failed to encrypt string", e);
		}
	}

	private static void writeFile(Path path, String value) throws IOException {
		try {
			try {
				Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS));
			} catch (FileAlreadyExistsException ignored) {
				// Permissions are updated below.
			}

			Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS);
		} catch (UnsupportedOperationException ignored) {
			// POSIX permissions are not supported on this filesystem.
		}

		Files.writeString(path, value);
	}

	public String read(Path path) throws IOException, LoomNativePlatformException {
		Objects.requireNonNull(path, "path");
		EncryptedValue encrypted = parse(readFile(path));
		Base64.Decoder decoder = Base64.getDecoder();
		byte[] wrappedKey;
		byte[] initializationVector;
		byte[] ciphertext;

		try {
			wrappedKey = decoder.decode(encrypted.wrappedKey());
			initializationVector = decoder.decode(encrypted.initializationVector());
			ciphertext = decoder.decode(encrypted.ciphertext());
		} catch (IllegalArgumentException e) {
			throw new IOException("Encrypted string file contains invalid Base64", e);
		}

		if (wrappedKey.length == 0 || initializationVector.length != INITIALIZATION_VECTOR_BYTES || ciphertext.length < GCM_TAG_BITS / Byte.SIZE) {
			throw new IOException("Encrypted string file contains invalid values");
		}

		SecretKey key = keyStore.read(new EncryptionKeyStore.StoredKey("AES", wrappedKey));

		if (!"AES".equalsIgnoreCase(key.getAlgorithm())) {
			throw new IOException("Encryption key store returned an unsupported algorithm: " + key.getAlgorithm());
		}

		try {
			Cipher cipher = createCipher(Cipher.DECRYPT_MODE, key, initializationVector);
			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (AEADBadTagException e) {
			throw new IOException("Encrypted string authentication failed", e);
		} catch (GeneralSecurityException e) {
			throw new IOException("Failed to decrypt string", e);
		}
	}

	private static Cipher createCipher(int mode, SecretKey key, byte[] initializationVector) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, initializationVector));
		cipher.updateAAD(AAD);
		return cipher;
	}

	private static EncryptedValue parse(byte[] file) throws IOException {
		JsonObject object;

		try {
			JsonElement element = JsonParser.parseString(new String(file, StandardCharsets.UTF_8));

			if (!element.isJsonObject()) {
				throw new IllegalStateException("Encrypted string file must contain a JSON object");
			}

			object = element.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Invalid encrypted string file", e);
		}

		int version = parseVersion(object);

		if (version != VERSION) {
			throw new IOException("Unsupported encrypted string file version: " + version);
		}

		try {
			return GSON.fromJson(object, EncryptedValue.class);
		} catch (RuntimeException e) {
			throw new IOException("Invalid encrypted string file", e);
		}
	}

	private static int parseVersion(JsonObject object) throws IOException {
		JsonElement version = object.get("version");

		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
			throw new IOException("Invalid encrypted string file version");
		}

		try {
			return Integer.parseInt(version.getAsString());
		} catch (NumberFormatException e) {
			throw new IOException("Invalid encrypted string file version", e);
		}
	}

	private static byte[] readFile(Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	private record EncryptedValue(int version, String wrappedKey, String initializationVector, String ciphertext) {
		private EncryptedValue {
			Objects.requireNonNull(wrappedKey, "wrappedKey");
			Objects.requireNonNull(initializationVector, "initializationVector");
			Objects.requireNonNull(ciphertext, "ciphertext");
		}
	}
}
