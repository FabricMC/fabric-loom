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

import net.fabricmc.loom.task.launch.auth.MicrosoftLoginService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginServiceImpl
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProvider
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProviderImpl

/**
 * Manual authentication smoke test. This intentionally prints credentials and must not be used in
 * CI or with logs that will be retained or shared.
 */
final class MicrosoftAuthManualTest {
	private MicrosoftAuthManualTest() {
	}

	static void main(String[] args) {
		String clientId = args.length > 0 ? args[0] : System.getenv("LOOM_MICROSOFT_CLIENT_ID")

		if (!clientId) {
			throw new IllegalArgumentException("Pass the Microsoft client ID as the first argument or set LOOM_MICROSOFT_CLIENT_ID")
		}

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

		MinecraftAccessTokenProvider tokenProvider = new MinecraftAccessTokenProviderImpl()
		MinecraftAccessTokenProvider.AccessToken accessToken = tokenProvider.getAccessToken(clientId, login.refreshToken())

		println "Rotated Microsoft refresh token: ${accessToken.refreshToken()}"
		println "Minecraft access token: ${accessToken.accessToken()}"
		println "Minecraft access token expires in: ${accessToken.expiresIn()} seconds"
	}
}
