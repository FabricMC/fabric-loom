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

import spock.lang.Specification

import net.fabricmc.loom.task.launch.auth.MicrosoftAuthService
import net.fabricmc.loom.task.launch.auth.MicrosoftLoginServiceImpl
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProviderImpl

class MicrosoftLoginFlowTest extends Specification {
	def "login reports the device code, polls, and returns durable account details"() {
		given:
		def auth = Mock(MicrosoftAuthService)
		def deviceCode = new MicrosoftAuthService.DeviceCode(
				"device-code", "ABCD-EFGH", URI.create("https://microsoft.com/devicelogin"), 900, 1, "Sign in"
				)
		def microsoftToken = new MicrosoftAuthService.MicrosoftToken.Success("microsoft-token", "refresh-token", "Bearer", 3600)
		def xboxUser = new MicrosoftAuthService.XboxToken("xbox-user", "user-hash")
		def xsts = new MicrosoftAuthService.XboxToken("xsts", "user-hash")
		def minecraftToken = new MicrosoftAuthService.MinecraftToken("minecraft-token", "Bearer", 86400)
		def entitlements = new MicrosoftAuthService.MinecraftEntitlements(true, true)
		def profile = new MicrosoftAuthService.MinecraftProfile("uuid", "Player")
		def sleeps = []
		def reportedCode
		def login = new MicrosoftLoginServiceImpl(auth, { sleeps.add(it) })

		when:
		def result = login.login("client-id") { reportedCode = it }

		then:
		1 * auth.requestDeviceCode("client-id") >> deviceCode
		2 * auth.requestMicrosoftToken("client-id", "device-code") >>> [
			new MicrosoftAuthService.MicrosoftToken.Polling(
			MicrosoftAuthService.MicrosoftToken.Polling.Status.AUTHORIZATION_PENDING, null
			),
			microsoftToken
		]
		1 * auth.authenticateXboxUser("microsoft-token") >> xboxUser
		1 * auth.authorizeMinecraftServices(xboxUser) >> xsts
		1 * auth.loginToMinecraft("user-hash", "xsts") >> minecraftToken
		1 * auth.fetchMinecraftEntitlements("minecraft-token") >> entitlements
		1 * auth.fetchMinecraftProfile("minecraft-token") >> Optional.of(profile)
		reportedCode == deviceCode
		sleeps*.seconds == [1, 1]
		result.refreshToken() == "refresh-token"
		result.profile() == profile
		result.entitlements().ownsMinecraft()
	}

	def "per-launch provider refreshes the complete chain and returns the rotated refresh token"() {
		given:
		def auth = Mock(MicrosoftAuthService)
		def microsoftToken = new MicrosoftAuthService.MicrosoftToken.Success("microsoft-token", "new-refresh-token", "Bearer", 3600)
		def xboxUser = new MicrosoftAuthService.XboxToken("xbox-user", "user-hash")
		def xsts = new MicrosoftAuthService.XboxToken("xsts", "user-hash")
		def minecraftToken = new MicrosoftAuthService.MinecraftToken("minecraft-token", "Bearer", 86400)
		def provider = new MinecraftAccessTokenProviderImpl(auth)
		def rotatedRefreshTokens = []

		when:
		def result = provider.getAccessToken("client-id", "old-refresh-token") { rotatedRefreshTokens.add(it) }

		then:
		1 * auth.refreshMicrosoftToken("client-id", "old-refresh-token") >> microsoftToken
		1 * auth.authenticateXboxUser("microsoft-token") >> xboxUser
		1 * auth.authorizeMinecraftServices(xboxUser) >> xsts
		1 * auth.loginToMinecraft("user-hash", "xsts") >> minecraftToken
		1 * auth.fetchMinecraftEntitlements("minecraft-token") >> new MicrosoftAuthService.MinecraftEntitlements(true, false)
		result.accessToken() == "minecraft-token"
		result.refreshToken() == "new-refresh-token"
		result.expiresIn() == 86400
		rotatedRefreshTokens == ["new-refresh-token"]
	}

	def "per-launch provider reports the rotated token before a downstream entitlement failure"() {
		given:
		def auth = Stub(MicrosoftAuthService) {
			refreshMicrosoftToken(_, _) >> new MicrosoftAuthService.MicrosoftToken.Success("microsoft-token", "new-refresh-token", "Bearer", 3600)
			authenticateXboxUser(_) >> new MicrosoftAuthService.XboxToken("xbox-user", "user-hash")
			authorizeMinecraftServices(_) >> new MicrosoftAuthService.XboxToken("xsts", "user-hash")
			loginToMinecraft(_, _) >> new MicrosoftAuthService.MinecraftToken("minecraft-token", "Bearer", 86400)
			fetchMinecraftEntitlements(_) >> new MicrosoftAuthService.MinecraftEntitlements(false, false)
		}
		def provider = new MinecraftAccessTokenProviderImpl(auth)
		def rotatedRefreshTokens = []

		when:
		provider.getAccessToken("client-id", "old-refresh-token") { rotatedRefreshTokens.add(it) }

		then:
		def exception = thrown IOException
		exception.message == "The Microsoft account is not entitled to play Minecraft"
		rotatedRefreshTokens == ["new-refresh-token"]
	}
}
