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
import java.util.function.Consumer

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.task.launch.auth.MicrosoftAccountStore
import net.fabricmc.loom.task.launch.auth.MicrosoftAuthService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginTask
import net.fabricmc.loom.task.launch.auth.MicrosoftLogoutTask
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProvider
import net.fabricmc.loom.util.nativeplatform.LoomNativePlatformException

import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.eq
import static org.mockito.Mockito.inOrder
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.never
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class MicrosoftAuthTasksTest extends Specification {
	@TempDir
	Path tempDir

	def "login verifies an existing account and saves its rotated refresh token"() {
		given:
		Path accountFile = Files.writeString(tempDir.resolve("account.json"), "stored")
		def store = mock(MicrosoftAccountStore)
		def loginService = mock(MicrosoftLoginService)
		def accessTokenProvider = mock(MinecraftAccessTokenProvider)
		def account = new MicrosoftAccountStore.Account("stored-client-id", "old-refresh-token", "profile-id", "Player")
		when(store.read()).thenReturn(account)
		when(accessTokenProvider.getAccessToken(eq("stored-client-id"), eq("old-refresh-token"), any(Consumer))).thenAnswer { invocation ->
			Consumer<String> consumer = invocation.getArgument(2)
			consumer.accept("rotated-refresh-token")
			return new MinecraftAccessTokenProvider.AccessToken("minecraft-access-token", "rotated-refresh-token", 3600)
		}
		def task = loginTask(accountFile, "", store, loginService, accessTokenProvider)

		when:
		task.login()

		then:
		task.storeCreated
		assert verify(store).read() == null
		assert verify(accessTokenProvider).getAccessToken(eq("stored-client-id"), eq("old-refresh-token"), any(Consumer)) == null
		verify(store).write(account.withRefreshToken("rotated-refresh-token"))
		verify(store, never()).prepare()
		assert verify(loginService, never()).login(any(), any()) == null
	}

	def "login replaces an unreadable stored account after successful authentication"() {
		given:
		Path accountFile = Files.writeString(tempDir.resolve("account.json"), "stored")
		def store = mock(MicrosoftAccountStore)
		def loginService = mock(MicrosoftLoginService)
		def accessTokenProvider = mock(MinecraftAccessTokenProvider)
		def profile = new MicrosoftAuthService.MinecraftProfile("new-profile-id", "NewPlayer")
		def result = new MicrosoftLoginService.LoginResult(
				"new-refresh-token",
				profile,
				new MicrosoftAuthService.MinecraftEntitlements(true, true)
				)
		when(store.read()).thenThrow(new IOException("invalid stored account"))
		when(loginService.login(eq("client-id"), any(Consumer))).thenReturn(result)
		def task = loginTask(accountFile, "client-id", store, loginService, accessTokenProvider)

		when:
		task.login()

		then:
		def order = inOrder(store, loginService)
		assert order.verify(store).read() == null
		order.verify(store).prepare()
		assert order.verify(loginService).login(eq("client-id"), any(Consumer)) == null
		order.verify(store).write(new MicrosoftAccountStore.Account("client-id", "new-refresh-token", "new-profile-id", "NewPlayer"))
		assert verify(accessTokenProvider, never()).getAccessToken(any(), any(), any()) == null
		verify(store, never()).delete()
	}

	def "login replaces an account whose refresh token can no longer authenticate"() {
		given:
		Path accountFile = Files.writeString(tempDir.resolve("account.json"), "stored")
		def store = mock(MicrosoftAccountStore)
		def loginService = mock(MicrosoftLoginService)
		def accessTokenProvider = mock(MinecraftAccessTokenProvider)
		def account = new MicrosoftAccountStore.Account("stored-client-id", "expired-refresh-token", "profile-id", "Player")
		def profile = new MicrosoftAuthService.MinecraftProfile("new-profile-id", "NewPlayer")
		def result = new MicrosoftLoginService.LoginResult(
				"new-refresh-token",
				profile,
				new MicrosoftAuthService.MinecraftEntitlements(true, true)
				)
		when(store.read()).thenReturn(account)
		when(accessTokenProvider.getAccessToken(eq("stored-client-id"), eq("expired-refresh-token"), any(Consumer)))
				.thenThrow(new IOException("refresh token expired"))
		when(loginService.login(eq("stored-client-id"), any(Consumer))).thenReturn(result)
		def task = loginTask(accountFile, "client-id", store, loginService, accessTokenProvider)

		when:
		task.login()

		then:
		def order = inOrder(store, accessTokenProvider, loginService)
		assert order.verify(store).read() == null
		assert order.verify(accessTokenProvider).getAccessToken(eq("stored-client-id"), eq("expired-refresh-token"), any(Consumer)) == null
		order.verify(store).prepare()
		assert order.verify(loginService).login(eq("stored-client-id"), any(Consumer)) == null
		order.verify(store).write(new MicrosoftAccountStore.Account("stored-client-id", "new-refresh-token", "new-profile-id", "NewPlayer"))
		verify(store, never()).delete()
	}

	def "login reports an empty client ID before creating storage"() {
		given:
		def task = loginTask(tempDir.resolve("missing.json"), "")

		when:
		task.login()

		then:
		def exception = thrown(GradleException)
		exception.message.contains("Constants.MICROSOFT_CLIENT_ID")
		!task.storeCreated
	}

	def "login prepares storage before authentication and saves the durable account"() {
		given:
		def store = mock(MicrosoftAccountStore)
		def loginService = mock(MicrosoftLoginService)
		def profile = new MicrosoftAuthService.MinecraftProfile("profile-id", "Player")
		def result = new MicrosoftLoginService.LoginResult(
				"refresh-token",
				profile,
				new MicrosoftAuthService.MinecraftEntitlements(true, true)
				)
		when(loginService.login(eq("client-id"), any(Consumer))).thenReturn(result)
		def task = loginTask(tempDir.resolve("missing.json"), "client-id", store, loginService)

		when:
		task.login()

		then:
		def order = inOrder(store, loginService)
		order.verify(store).prepare()
		assert order.verify(loginService).login(eq("client-id"), any(Consumer)) == null
		verify(store).write(new MicrosoftAccountStore.Account("client-id", "refresh-token", "profile-id", "Player"))
	}

	def "login exposes secure storage preparation errors"() {
		given:
		def store = mock(MicrosoftAccountStore)
		def loginService = mock(MicrosoftLoginService)
		def task = loginTask(tempDir.resolve("missing.json"), "client-id", store, loginService)
		when(store.prepare()).thenThrow(new LoomNativePlatformException("TPM secure storage is unavailable"))

		when:
		task.login()

		then:
		def exception = thrown(GradleException)
		exception.message == "Microsoft login failed: TPM secure storage is unavailable"
		assert verify(loginService, never()).login(any(), any()) == null
	}

	def "logout removes an orphaned platform key when no account exists"() {
		given:
		def store = mock(MicrosoftAccountStore)
		def task = logoutTask(tempDir.resolve("missing.json"), store)

		when:
		task.logout()

		then:
		task.storeCreated
		verify(store).delete()
	}

	def "logout deletes an existing account"() {
		given:
		Path accountFile = Files.writeString(tempDir.resolve("account.json"), "stored")
		def store = mock(MicrosoftAccountStore)
		def task = logoutTask(accountFile, store)

		when:
		task.logout()

		then:
		verify(store).delete()
		verify(store, never()).prepare()
	}

	def "logout remains idempotent on an unsupported platform"() {
		given:
		Path accountFile = tempDir.resolve("missing.json")
		def task = logoutTask(accountFile, null, new UnsupportedOperationException("unsupported"))

		when:
		task.logout()

		then:
		!Files.exists(accountFile)
	}

	def "logout deletes an account file on an unsupported platform"() {
		given:
		Path accountFile = Files.writeString(tempDir.resolve("account.json"), "stored")
		def task = logoutTask(accountFile, null, new UnsupportedOperationException("unsupported"))

		when:
		task.logout()

		then:
		!Files.exists(accountFile)
	}

	private static TestMicrosoftLoginTask loginTask(Path accountFile, String clientId,
			MicrosoftAccountStore store = null, MicrosoftLoginService loginService = null,
			MinecraftAccessTokenProvider accessTokenProvider = null) {
		def project = ProjectBuilder.builder().build()
		def task = project.tasks.create("microsoftLoginTest", TestMicrosoftLoginTask)
		task.testAccountPath = accountFile
		task.clientId = clientId
		task.accountStore = store
		task.loginService = loginService
		task.accessTokenProvider = accessTokenProvider
		return task
	}

	private static TestMicrosoftLogoutTask logoutTask(Path accountFile, MicrosoftAccountStore store = null,
			RuntimeException storeCreationFailure = null) {
		def project = ProjectBuilder.builder().build()
		def task = project.tasks.create("microsoftLogoutTest", TestMicrosoftLogoutTask)
		task.testAccountPath = accountFile
		task.accountStore = store
		task.storeCreationFailure = storeCreationFailure
		return task
	}

	static abstract class TestMicrosoftLoginTask extends MicrosoftLoginTask {
		Path testAccountPath
		String clientId
		MicrosoftAccountStore accountStore
		MicrosoftLoginService loginService
		MinecraftAccessTokenProvider accessTokenProvider
		boolean storeCreated

		@Override
		protected Path getAccountPath() {
			return testAccountPath
		}

		@Override
		protected String getClientId() {
			return clientId
		}

		@Override
		protected MicrosoftAccountStore createAccountStore(Path path) {
			storeCreated = true
			return accountStore
		}

		@Override
		protected MicrosoftLoginService createLoginService() {
			return loginService
		}

		@Override
		protected MinecraftAccessTokenProvider createAccessTokenProvider() {
			return accessTokenProvider
		}
	}

	static abstract class TestMicrosoftLogoutTask extends MicrosoftLogoutTask {
		Path testAccountPath
		MicrosoftAccountStore accountStore
		RuntimeException storeCreationFailure
		boolean storeCreated

		@Override
		protected Path getAccountPath() {
			return testAccountPath
		}

		@Override
		protected MicrosoftAccountStore createAccountStore(Path path) {
			storeCreated = true

			if (storeCreationFailure != null) {
				throw storeCreationFailure
			}

			return accountStore
		}
	}
}
