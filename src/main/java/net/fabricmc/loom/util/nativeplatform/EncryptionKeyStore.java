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

import java.util.Arrays;
import java.util.Objects;

import javax.crypto.SecretKey;

/// Stores a Java encryption key using protection supplied by the host operating system.
///
/// The returned [StoredKey] is safe to persist, but can only be read while the corresponding
/// platform key remains available. Encryption of application data remains independent of the
/// platform because [#read(StoredKey)] returns a standard [SecretKey].
public interface EncryptionKeyStore {
	/// Prepares and verifies the platform key before credentials are acquired.
	void prepare() throws LoomNativePlatformException;

	StoredKey store(SecretKey key) throws LoomNativePlatformException;

	SecretKey read(StoredKey key) throws LoomNativePlatformException;

	/// Deletes the platform key. Any values returned by [#store(SecretKey)] become unreadable.
	void delete() throws LoomNativePlatformException;

	/// Controls whether the platform is allowed to require interactive user consent.
	enum UserInteraction {
		REQUIRED,
		DISABLED
	}

	record StoredKey(String algorithm, byte[] data) {
		public StoredKey {
			Objects.requireNonNull(algorithm, "algorithm");
			Objects.requireNonNull(data, "data");

			if (algorithm.isBlank()) {
				throw new IllegalArgumentException("algorithm must not be blank");
			}

			if (data.length == 0) {
				throw new IllegalArgumentException("data must not be empty");
			}

			data = data.clone();
		}

		@Override
		public byte[] data() {
			return data.clone();
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj || obj instanceof StoredKey other
					&& algorithm.equals(other.algorithm)
					&& Arrays.equals(data, other.data);
		}

		@Override
		public int hashCode() {
			return 31 * algorithm.hashCode() + Arrays.hashCode(data);
		}
	}
}
