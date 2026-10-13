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

import net.fabricmc.loom.task.launch.auth.MicrosoftAccountStore
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginServiceImpl
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProvider
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProviderImpl
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStoreFactory

/**
 * Manual authentication smoke test. This intentionally prints credentials and must not be used in
 * CI or with logs that will be retained or shared.
 */
final class MicrosoftAuthManualTest {
	private static final Path STORED_LOGIN_FILE = Path.of("microsoft-auth-test.json")
	private static final String KEY_NAME = "FabricLoomMicrosoftAuthManualTestEncryptionKey"

	private MicrosoftAuthManualTest() {
	}

	static void main(String[] args) {
		String clientId = Constants.MICROSOFT_CLIENT_ID
		EncryptionKeyStore keyStore = EncryptionKeyStoreFactory.create(KEY_NAME, EncryptionKeyStore.UserInteraction.REQUIRED)
		MicrosoftAccountStore accountStore = new MicrosoftAccountStore(STORED_LOGIN_FILE, keyStore)
		boolean hasStoredLogin = accountStore.exists()

		if (!hasStoredLogin) {
			accountStore.delete()
		}

		accountStore.prepare()
		MicrosoftAccountStore.Account account

		if (hasStoredLogin) {
			account = accountStore.read()

			if (clientId != account.clientId()) {
				throw new IllegalArgumentException("The stored login uses a different Microsoft client ID; delete ${STORED_LOGIN_FILE} to authenticate again")
			}

			println "Loaded encrypted login from ${STORED_LOGIN_FILE.toAbsolutePath()}"
		} else {
			account = login(accountStore, clientId)
		}

		println "Profile: ${account.profileName()} (${account.profileId()})"

		MinecraftAccessTokenProvider tokenProvider = new MinecraftAccessTokenProviderImpl()
		MinecraftAccessTokenProvider.AccessToken accessToken = tokenProvider.getAccessToken(clientId, account.refreshToken()) { refreshToken ->
			accountStore.write(account.withRefreshToken(refreshToken))
			println "Rotated Microsoft refresh token: ${refreshToken}"
			println "Updated encrypted login in ${STORED_LOGIN_FILE.toAbsolutePath()}"
		}

		println "Minecraft access token: ${accessToken.accessToken()}"
		println "Minecraft access token expires in: ${accessToken.expiresIn()} seconds"
	}

	private static MicrosoftAccountStore.Account login(MicrosoftAccountStore accountStore, String clientId) {
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

		MicrosoftAccountStore.Account account = new MicrosoftAccountStore.Account(
				clientId,
				login.refreshToken(),
				login.profile().id(),
				login.profile().name()
				)
		accountStore.write(account)
		println "Stored encrypted login in ${STORED_LOGIN_FILE.toAbsolutePath()}"
		return account
	}
}
