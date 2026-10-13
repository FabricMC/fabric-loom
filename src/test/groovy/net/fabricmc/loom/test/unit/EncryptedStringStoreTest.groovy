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

package net.fabricmc.loom.test.unit

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.util.EncryptedStringStore
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore

class EncryptedStringStoreTest extends Specification {
	@TempDir
	Path directory

	EncryptedStringStore store = new EncryptedStringStore(new ReversibleTestKeyStore())

	def "writes and reads an encrypted string"() {
		given:
		Path file = directory.resolve("secret.bin")

		when:
		store.write(file, "refresh-token-value")

		then:
		store.read(file) == "refresh-token-value"
		!new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("refresh-token-value")
	}

	def "overwrites an existing encrypted string"() {
		given:
		Path file = directory.resolve("secret.bin")
		store.write(file, "old-token")

		when:
		store.write(file, "new-token")

		then:
		store.read(file) == "new-token"
	}

	@Requires({ os.linux || os.macOs })
	def "restricts encrypted files to the owner"() {
		given:
		Path file = directory.resolve("secret.bin")

		when:
		store.write(file, "refresh-token-value")

		then:
		Files.getPosixFilePermissions(file) == PosixFilePermissions.fromString("rw-------")
	}

	def "rejects modified ciphertext"() {
		given:
		Path file = directory.resolve("secret.bin")
		store.write(file, "refresh-token-value")
		def envelope = new JsonSlurper().parse(file.toFile())
		byte[] ciphertext = Base64.decoder.decode(envelope.ciphertext)
		ciphertext[ciphertext.length - 1] ^= 1
		envelope.ciphertext = Base64.encoder.encodeToString(ciphertext)
		Files.writeString(file, JsonOutput.toJson(envelope))

		when:
		store.read(file)

		then:
		def exception = thrown IOException
		exception.message == "Encrypted string authentication failed"
	}

	def "rejects an invalid file"() {
		given:
		Path file = directory.resolve("secret.bin")
		Files.write(file, [1, 2, 3] as byte[])

		when:
		store.read(file)

		then:
		thrown IOException
	}

	def "rejects an envelope with missing fields"() {
		given:
		Path file = directory.resolve("secret.bin")
		Files.writeString(file, '{"version":1}')

		when:
		store.read(file)

		then:
		thrown IOException
	}

	def "checks the version before parsing the version-specific format"() {
		given:
		Path file = directory.resolve("secret.bin")
		Files.writeString(file, '{"version":2,"differentFormat":true}')

		when:
		store.read(file)

		then:
		def exception = thrown IOException
		exception.message == "Unsupported encrypted string file version: 2"
	}

	def "stored keys use value equality"() {
		given:
		def first = new EncryptionKeyStore.StoredKey("AES", [1, 2, 3] as byte[])
		def second = new EncryptionKeyStore.StoredKey("AES", [1, 2, 3] as byte[])

		expect:
		first == second
		first.hashCode() == second.hashCode()
	}

	private static final class ReversibleTestKeyStore implements EncryptionKeyStore {
		@Override
		void prepare() {
		}

		@Override
		StoredKey store(SecretKey key) {
			return new StoredKey(key.algorithm, transform(key.encoded))
		}

		@Override
		SecretKey read(StoredKey key) {
			return new SecretKeySpec(transform(key.data()), key.algorithm())
		}

		@Override
		void delete() {
		}

		private static byte[] transform(byte[] input) {
			return input.collect { (byte) (it ^ 0x5A) } as byte[]
		}
	}
}
