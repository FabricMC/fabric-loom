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

package net.fabricmc.loom.test.unit.nativeplatform

import javax.crypto.SecretKey

import spock.lang.Specification

import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore

class EncryptionKeyStoreTest extends Specification {
	EncryptionKeyStore store = EncryptionKeyStore.FALLBACK

	def "fallback lifecycle operations do not require platform state"() {
		when:
		store.prepare()
		store.delete()

		then:
		noExceptionThrown()
	}

	def "fallback stores and reads an encoded key"() {
		given:
		byte[] encoded = (0..<32) as byte[]
		SecretKey original = new BorrowedSecretKey("AES", encoded)

		when:
		def stored = store.store(original)
		SecretKey recovered = store.read(stored)

		then:
		stored.algorithm() == "AES"
		stored.data() == encoded
		recovered.algorithm == "AES"
		recovered.encoded == encoded
	}

	def "fallback defensively copies key data"() {
		given:
		byte[] encoded = new byte[32]
		encoded[0] = 42

		when:
		def stored = store.store(new BorrowedSecretKey("AES", encoded))
		encoded[0] = 0
		byte[] storedData = stored.data()
		storedData[0] = 1

		then:
		stored.data()[0] == 42
		store.read(stored).encoded[0] == 42
	}

	def "fallback rejects an unencodable key"() {
		when:
		store.store(new BorrowedSecretKey("AES", encoded))

		then:
		def exception = thrown IllegalArgumentException
		exception.message == "key must be encodable"

		where:
		encoded << [null, new byte[0]]
	}

	private static final class BorrowedSecretKey implements SecretKey {
		private static final long serialVersionUID = 1L

		final String algorithm
		final String format = "RAW"
		private final byte[] encoded

		private BorrowedSecretKey(String algorithm, byte[] encoded) {
			this.algorithm = algorithm
			this.encoded = encoded
		}

		@Override
		byte[] getEncoded() {
			return encoded
		}
	}
}
