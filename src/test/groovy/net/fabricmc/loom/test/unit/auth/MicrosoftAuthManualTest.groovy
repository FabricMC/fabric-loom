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

import com.google.gson.Gson

import net.fabricmc.loom.task.launch.auth.MicrosoftLoginService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginServiceImpl
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProvider
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProviderImpl
import net.fabricmc.loom.util.EncryptedStringStore
import net.fabricmc.loom.util.nativeplatform.WindowsEncryptionKeyStore

/**
 * Manual authentication smoke test. This intentionally prints credentials and must not be used in
 * CI or with logs that will be retained or shared.
 */
final class MicrosoftAuthManualTest {
	private static final int STORED_LOGIN_VERSION = 1
	private static final Path STORED_LOGIN_FILE = Path.of("microsoft-auth-test.json")
	private static final Gson GSON = new Gson()

	private MicrosoftAuthManualTest() {
	}

	static void main(String[] args) {
		String requestedClientId = args.length > 0 ? args[0] : System.getenv("LOOM_MICROSOFT_CLIENT_ID")
		boolean hasStoredLogin = Files.exists(STORED_LOGIN_FILE)

		if (!hasStoredLogin && !requestedClientId) {
			throw new IllegalArgumentException("Pass the Microsoft client ID as the first argument or set LOOM_MICROSOFT_CLIENT_ID")
		}

		WindowsEncryptionKeyStore keyStore = new WindowsEncryptionKeyStore()
		keyStore.prepare()
		EncryptedStringStore storedLoginStore = new EncryptedStringStore(keyStore)
		StoredLogin storedLogin

		if (hasStoredLogin) {
			storedLogin = readStoredLogin(storedLoginStore)

			if (requestedClientId && requestedClientId != storedLogin.clientId) {
				throw new IllegalArgumentException("The stored login uses a different Microsoft client ID; delete ${STORED_LOGIN_FILE} to authenticate again")
			}

			println "Loaded encrypted login from ${STORED_LOGIN_FILE.toAbsolutePath()}"
		} else {
			storedLogin = login(storedLoginStore, requestedClientId)
		}

		String clientId = storedLogin.clientId
		println "Profile: ${storedLogin.profileName} (${storedLogin.profileId})"
		println "Can play Minecraft: ${storedLogin.canPlayMinecraft}"
		println "Owns Minecraft: ${storedLogin.ownsMinecraft}"

		MinecraftAccessTokenProvider tokenProvider = new MinecraftAccessTokenProviderImpl()
		MinecraftAccessTokenProvider.AccessToken accessToken = tokenProvider.getAccessToken(clientId, storedLogin.refreshToken)
		storedLogin = storedLogin.withRefreshToken(accessToken.refreshToken())
		storedLoginStore.write(STORED_LOGIN_FILE, GSON.toJson(storedLogin))

		println "Rotated Microsoft refresh token: ${accessToken.refreshToken()}"
		println "Minecraft access token: ${accessToken.accessToken()}"
		println "Minecraft access token expires in: ${accessToken.expiresIn()} seconds"
		println "Updated encrypted login in ${STORED_LOGIN_FILE.toAbsolutePath()}"
	}

	private static StoredLogin login(EncryptedStringStore storedLoginStore, String clientId) {
		MicrosoftLoginService loginService = new MicrosoftLoginServiceImpl()
		MicrosoftLoginService.LoginResult login = loginService.login(clientId) { deviceCode ->
			println deviceCode.message()
			println "Verification URI: ${deviceCode.verificationUri()}"
			println "User code: ${deviceCode.userCode()}"
		}

		println "Profile: ${login.profile().name()} (${login.profile().id()})"
		println "Can play Minecraft: ${login.entitlements().canPlayMinecraft()}"
		println "Owns Minecraft: ${login.entitlements().ownsMinecraft()}"
		println "Microsoft refresh token: ${login.refreshToken()}"

		StoredLogin storedLogin = new StoredLogin(
				STORED_LOGIN_VERSION,
				clientId,
				login.refreshToken(),
				login.profile().id(),
				login.profile().name(),
				login.entitlements().canPlayMinecraft(),
				login.entitlements().ownsMinecraft()
				)
		storedLoginStore.write(STORED_LOGIN_FILE, GSON.toJson(storedLogin))
		println "Stored encrypted login in ${STORED_LOGIN_FILE.toAbsolutePath()}"
		return storedLogin
	}

	private static StoredLogin readStoredLogin(EncryptedStringStore storedLoginStore) {
		StoredLogin storedLogin = GSON.fromJson(storedLoginStore.read(STORED_LOGIN_FILE), StoredLogin)

		if (storedLogin == null || storedLogin.version != STORED_LOGIN_VERSION) {
			throw new IllegalStateException("Unsupported stored login in ${STORED_LOGIN_FILE}")
		}

		if (!storedLogin.clientId || !storedLogin.refreshToken || !storedLogin.profileId || !storedLogin.profileName) {
			throw new IllegalStateException("Stored login in ${STORED_LOGIN_FILE} is missing required fields")
		}

		return storedLogin
	}

	private static final class StoredLogin {
		int version
		String clientId
		String refreshToken
		String profileId
		String profileName
		boolean canPlayMinecraft
		boolean ownsMinecraft

		private StoredLogin() {
		}

		private StoredLogin(int version, String clientId, String refreshToken, String profileId, String profileName,
		boolean canPlayMinecraft, boolean ownsMinecraft) {
			this.version = version
			this.clientId = clientId
			this.refreshToken = refreshToken
			this.profileId = profileId
			this.profileName = profileName
			this.canPlayMinecraft = canPlayMinecraft
			this.ownsMinecraft = ownsMinecraft
		}

		private StoredLogin withRefreshToken(String refreshToken) {
			return new StoredLogin(version, clientId, refreshToken, profileId, profileName, canPlayMinecraft, ownsMinecraft)
		}
	}
}
