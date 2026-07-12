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

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

import spock.lang.Requires
import spock.lang.Specification

import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore
import net.fabricmc.loom.util.nativeplatform.LoomNativePlatformException
import net.fabricmc.loom.util.nativeplatform.MacOSEncryptionKeyStore

@Requires({
	os.macOs
})
class MacOSEncryptionKeyStoreTest extends Specification {
	String keyName = "FabricLoomEncryptionKeyStoreTest-${UUID.randomUUID()}"
	MacOSEncryptionKeyStore store = new MacOSEncryptionKeyStore(keyName, EncryptionKeyStore.UserInteraction.DISABLED)

	def cleanup() {
		store.delete()
	}

	def "prepares a persistent key without user interaction"() {
		when:
		store.prepare()
		new MacOSEncryptionKeyStore(keyName, EncryptionKeyStore.UserInteraction.DISABLED).prepare()

		then:
		noExceptionThrown()
	}

	def "stores and reads a Java encryption key without user interaction"() {
		given:
		SecretKey original = aesKey()

		when:
		def stored = store.store(original)
		SecretKey recovered = store.read(stored)

		then:
		stored.algorithm() == "AES"
		stored.data() != original.encoded
		recovered.algorithm == "AES"
		recovered.encoded == original.encoded
	}

	def "storing a key does not clear caller-owned key bytes"() {
		given:
		byte[] encoded = new byte[32]
		encoded[0] = 42
		byte[] expected = encoded.clone()
		SecretKey key = new BorrowedSecretKey(encoded)

		when:
		store.store(key)

		then:
		encoded == expected
	}

	def "persisted macOS key can be reopened by another store instance"() {
		given:
		SecretKey original = aesKey()
		def stored = store.store(original)
		def reopened = new MacOSEncryptionKeyStore(keyName, EncryptionKeyStore.UserInteraction.DISABLED)

		expect:
		reopened.read(stored).encoded == original.encoded
	}

	def "deleting the macOS key makes stored values unreadable"() {
		given:
		def stored = store.store(aesKey())

		when:
		store.delete()
		store.read(stored)

		then:
		def exception = thrown LoomNativePlatformException
		exception.message.contains("does not exist")
	}

	private static SecretKey aesKey() {
		KeyGenerator generator = KeyGenerator.getInstance("AES")
		generator.init(256)
		return generator.generateKey()
	}

	private static final class BorrowedSecretKey implements SecretKey {
		private static final long serialVersionUID = 1L

		final String algorithm = "AES"
		final String format = "RAW"
		private final byte[] encoded

		private BorrowedSecretKey(byte[] encoded) {
			this.encoded = encoded
		}

		@Override
		byte[] getEncoded() {
			return encoded
		}
	}
}
