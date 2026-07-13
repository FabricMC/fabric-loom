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

import java.nio.file.Path
import java.util.function.Consumer

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.task.launch.auth.MicrosoftAccountStore
import net.fabricmc.loom.task.launch.auth.MicrosoftGameAuthentication
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProvider
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore
import net.fabricmc.loom.util.nativeplatform.LoomNativePlatformException

class MicrosoftGameAuthenticationTest extends Specification {
	@TempDir
	Path directory

	ReversibleTestKeyStore keyStore
	MicrosoftAccountStore accountStore

	def setup() {
		keyStore = new ReversibleTestKeyStore()
		accountStore = new MicrosoftAccountStore(directory.resolve("microsoft-auth.json"), keyStore)
	}

	def "returns launch arguments and persists the rotated refresh token"() {
		given:
		writeAccount()
		def provider = Mock(MinecraftAccessTokenProvider)
		def logger = Mock(Logger)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, logger)

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		1 * provider.getAccessToken("client-id", "old-refresh-token", _) >> { String clientId, String refreshToken, Consumer<String> consumer ->
			consumer.accept("new-refresh-token")
			return new MinecraftAccessTokenProvider.AccessToken("minecraft-access-token", "new-refresh-token", 3600)
		}
		arguments == [
			"--username",
			"Player",
			"--uuid",
			"0123456789abcdef0123456789abcdef",
			"--accessToken",
			"minecraft-access-token",
			"--userType",
			"msa"
		]
		accountStore.read() == new MicrosoftAccountStore.Account(
				"client-id", "new-refresh-token", "0123456789abcdef0123456789abcdef", "Player"
				)
	}

	def "does nothing when no stored account exists"() {
		given:
		def provider = Mock(MinecraftAccessTokenProvider)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, Mock(Logger))

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		0 * provider._
		arguments.empty
	}

	def "does not fail the launch when the stored account cannot be read"() {
		given:
		writeAccount()
		keyStore.failReads = true
		def provider = Mock(MinecraftAccessTokenProvider)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, Mock(Logger))

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		0 * provider._
		arguments.empty
	}

	def "does not fail the launch when refreshing the account fails"() {
		given:
		writeAccount()
		def provider = Mock(MinecraftAccessTokenProvider)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, Mock(Logger))

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		1 * provider.getAccessToken("client-id", "old-refresh-token", _) >> { throw new IOException("refresh failed") }
		arguments.empty
	}

	def "persists a rotated refresh token when downstream Minecraft authentication fails"() {
		given:
		writeAccount()
		def provider = Mock(MinecraftAccessTokenProvider)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, Mock(Logger))

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		1 * provider.getAccessToken("client-id", "old-refresh-token", _) >> { String clientId, String refreshToken, Consumer<String> consumer ->
			consumer.accept("new-refresh-token")
			throw new IOException("downstream authentication failed")
		}
		arguments.empty
		accountStore.read().refreshToken() == "new-refresh-token"
	}

	def "uses a refreshed access token when persisting its rotated refresh token fails"() {
		given:
		writeAccount()
		keyStore.failStores = true
		def provider = Mock(MinecraftAccessTokenProvider)
		def authentication = new MicrosoftGameAuthentication(accountStore, provider, Mock(Logger))

		when:
		def arguments = authentication.getLaunchArguments()

		then:
		1 * provider.getAccessToken("client-id", "old-refresh-token", _) >> { String clientId, String refreshToken, Consumer<String> consumer ->
			consumer.accept("new-refresh-token")
			return new MinecraftAccessTokenProvider.AccessToken("minecraft-access-token", "new-refresh-token", 3600)
		}
		arguments == [
			"--username",
			"Player",
			"--uuid",
			"0123456789abcdef0123456789abcdef",
			"--accessToken",
			"minecraft-access-token",
			"--userType",
			"msa"
		]
	}

	private void writeAccount() {
		accountStore.write(new MicrosoftAccountStore.Account(
				"client-id", "old-refresh-token", "0123456789abcdef0123456789abcdef", "Player"
				))
	}

	private static final class ReversibleTestKeyStore implements EncryptionKeyStore {
		boolean failReads
		boolean failStores

		@Override
		void prepare() {
		}

		@Override
		StoredKey store(SecretKey key) throws LoomNativePlatformException {
			if (failStores) {
				throw new LoomNativePlatformException("write failed")
			}

			return new StoredKey(key.algorithm, transform(key.encoded))
		}

		@Override
		SecretKey read(StoredKey key) throws LoomNativePlatformException {
			if (failReads) {
				throw new LoomNativePlatformException("read failed")
			}

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
