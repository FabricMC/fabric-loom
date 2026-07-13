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

package net.fabricmc.loom.test.unit.auth

import java.nio.file.Files
import java.nio.file.Path

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.extension.LoomFiles
import net.fabricmc.loom.task.launch.auth.MicrosoftAccountStore
import net.fabricmc.loom.util.EncryptedStringStore
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore

class MicrosoftAccountStoreTest extends Specification {
	@TempDir
	Path directory

	TestKeyStore keyStore = new TestKeyStore()

	def "uses a shared path beneath the Loom user cache"() {
		given:
		def files = Stub(LoomFiles) {
			getUserCache() >> directory.toFile()
		}

		expect:
		MicrosoftAccountStore.defaultPath(files) == directory.resolve("microsoft-auth.json")
	}

	def "writes and reads a validated encrypted account"() {
		given:
		Path file = directory.resolve("nested/microsoft-auth.json")
		def store = new MicrosoftAccountStore(file, keyStore)
		def account = new MicrosoftAccountStore.Account("client-id", "refresh-token", "profile-id", "Player")

		when:
		store.write(account)

		then:
		store.exists()
		store.read() == account
		!Files.readString(file).contains("refresh-token")
	}

	def "delegates storage preparation"() {
		given:
		def store = new MicrosoftAccountStore(directory.resolve("microsoft-auth.json"), keyStore)

		when:
		store.prepare()

		then:
		keyStore.prepareCount == 1
	}

	def "checks the account version before parsing version-specific fields"() {
		given:
		Path file = directory.resolve("microsoft-auth.json")
		new EncryptedStringStore(keyStore).write(file, '{"version":2,"differentFormat":true}')
		def store = new MicrosoftAccountStore(file, keyStore)

		when:
		store.read()

		then:
		def exception = thrown IOException
		exception.message == "Unsupported stored Microsoft account version: 2"
	}

	def "rejects missing account fields"() {
		given:
		Path file = directory.resolve("microsoft-auth.json")
		new EncryptedStringStore(keyStore).write(file, '{"version":1}')
		def store = new MicrosoftAccountStore(file, keyStore)

		when:
		store.read()

		then:
		def exception = thrown IOException
		exception.message == "Invalid stored Microsoft account"
	}

	def "deletes the account file before its platform key"() {
		given:
		Path file = directory.resolve("microsoft-auth.json")
		keyStore.fileThatMustBeDeleted = file
		def store = new MicrosoftAccountStore(file, keyStore)
		store.write(new MicrosoftAccountStore.Account("client-id", "refresh-token", "profile-id", "Player"))

		when:
		store.delete()

		then:
		!store.exists()
		keyStore.deleted
	}

	def "replaces only the refresh token"() {
		given:
		def account = new MicrosoftAccountStore.Account("client-id", "old-token", "profile-id", "Player")

		expect:
		account.withRefreshToken("new-token") == new MicrosoftAccountStore.Account("client-id", "new-token", "profile-id", "Player")
	}

	def "rejects a blank #field"() {
		when:
		new MicrosoftAccountStore.Account(clientId, refreshToken, profileId, profileName)

		then:
		thrown IllegalArgumentException

		where:
		field          | clientId   | refreshToken    | profileId    | profileName
		"client ID"    | ""         | "refresh-token" | "profile-id" | "Player"
		"refresh token" | "client-id" | ""              | "profile-id" | "Player"
		"profile ID"   | "client-id" | "refresh-token" | ""           | "Player"
		"profile name" | "client-id" | "refresh-token" | "profile-id" | ""
	}

	private static final class TestKeyStore implements EncryptionKeyStore {
		int prepareCount
		boolean deleted
		Path fileThatMustBeDeleted

		@Override
		void prepare() {
			prepareCount++
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
			if (fileThatMustBeDeleted != null && Files.exists(fileThatMustBeDeleted)) {
				throw new AssertionError("The encrypted account must be deleted first")
			}

			deleted = true
		}

		private static byte[] transform(byte[] input) {
			return input.collect { (byte) (it ^ 0x5A) } as byte[]
		}
	}
}
