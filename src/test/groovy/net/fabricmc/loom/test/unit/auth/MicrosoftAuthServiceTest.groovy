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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import spock.lang.Specification

import net.fabricmc.loom.task.launch.auth.MicrosoftAuthService
import net.fabricmc.loom.task.launch.auth.MicrosoftAuthServiceImpl

class MicrosoftAuthServiceTest extends Specification {
	Javalin server = Javalin.create().start(0)
	MicrosoftAuthService service = new MicrosoftAuthServiceImpl(endpoints())

	def cleanup() {
		server.stop()
	}

	def "requests a device code with the Xbox scopes"() {
		given:
		server.post("/device-code") { ctx ->
			assert ctx.formParam("client_id") == "client-id"
			assert ctx.formParam("scope") == "XboxLive.SignIn XboxLive.offline_access"
			json(ctx, [
				device_code: "device-code",
				user_code: "ABCD-EFGH",
				verification_uri: "https://microsoft.com/devicelogin",
				expires_in: 900,
				message: "Sign in"
			])
		}

		when:
		def result = service.requestDeviceCode("client-id")

		then:
		result.deviceCode() == "device-code"
		result.userCode() == "ABCD-EFGH"
		result.interval() == 5
	}

	def "returns structured device polling responses"() {
		given:
		server.post("/token") { ctx ->
			assert ctx.formParam("grant_type") == "urn:ietf:params:oauth:grant-type:device_code"
			ctx.status(HttpStatus.BAD_REQUEST)
			json(ctx, [
				error: "slow_down",
				error_description: "Poll less frequently"
			])
		}

		when:
		def result = service.requestMicrosoftToken("client-id", "device-code")

		then:
		result instanceof MicrosoftAuthService.MicrosoftToken.Polling
		result.status() == MicrosoftAuthService.MicrosoftToken.Polling.Status.SLOW_DOWN
	}

	def "refreshes and rotates the Microsoft refresh token"() {
		given:
		server.post("/token") { ctx ->
			assert ctx.formParam("client_id") == "client-id"
			assert ctx.formParam("grant_type") == "refresh_token"
			assert ctx.formParam("refresh_token") == "old-refresh-token"
			json(ctx, [
				access_token: "new-access-token",
				refresh_token: "new-refresh-token",
				token_type: "Bearer",
				expires_in: 3600
			])
		}

		when:
		def result = service.refreshMicrosoftToken("client-id", "old-refresh-token")

		then:
		result.accessToken() == "new-access-token"
		result.refreshToken() == "new-refresh-token"
	}

	def "rejects an XSTS token for a different user hash"() {
		given:
		server.post("/xsts") { ctx ->
			def body = new JsonSlurper().parseText(ctx.body())
			assert body.Properties.UserTokens == ["xbox-user-token"]
			ctx.result(xboxToken("xsts-token", "different-user"))
		}

		when:
		service.authorizeMinecraftServices(new MicrosoftAuthService.XboxToken("xbox-user-token", "expected-user"))

		then:
		def exception = thrown IOException
		exception.message == "Xbox authentication changed the user hash"
	}

	def "distinguishes ownership from the ability to play"() {
		given:
		server.get("/entitlements") { ctx ->
			assert ctx.header("Authorization") == "Bearer minecraft-token"
			assert ctx.queryParam("requestId") != null
			json(ctx, [items: items.collect { [name: it] }])
		}

		when:
		def result = service.fetchMinecraftEntitlements("minecraft-token")

		then:
		result.canPlayMinecraft() == canPlay
		result.ownsMinecraft() == owns

		where:
		items                                   | canPlay | owns
		[
			"game_minecraft",
			"product_minecraft"
		] | true    | true
		["game_minecraft"]                      | true    | false
		[]                                        | false   | false
	}

	def "returns an empty profile for an account without a Java profile"() {
		given:
		server.get("/profile") { ctx ->
			ctx.status(HttpStatus.NOT_FOUND)
		}

		expect:
		service.fetchMinecraftProfile("minecraft-token").isEmpty()
	}

	def "parses a complete Minecraft login and profile"() {
		given:
		server.post("/minecraft-login") { ctx ->
			def body = new JsonSlurper().parseText(ctx.body())
			assert body.xtoken == "XBL3.0 x=user-hash;xsts-token"
			assert body.platform == "PC_LAUNCHER"
			json(ctx, [access_token: "minecraft-token", token_type: "Bearer", expires_in: 86400])
		}
		server.get("/profile") { ctx ->
			json(ctx, [
				id: "0123456789abcdef0123456789abcdef",
				name: "Player",
				skins: [
					[id: "skin-id", state: "ACTIVE", url: "https://textures.minecraft.net/skin", variant: "CLASSIC"]
				],
				capes: []
			])
		}

		when:
		def token = service.loginToMinecraft("user-hash", "xsts-token")
		def profile = service.fetchMinecraftProfile(token.accessToken()).orElseThrow()

		then:
		token.expiresIn() == 86400
		profile.name() == "Player"
		profile.id() == "0123456789abcdef0123456789abcdef"
	}

	def "rejects a successful response with missing required fields"() {
		given:
		server.post("/minecraft-login") { ctx ->
			json(ctx, [token_type: "Bearer", expires_in: 86400])
		}

		when:
		service.loginToMinecraft("user-hash", "xsts-token")

		then:
		def exception = thrown IOException
		exception.message.contains("returned invalid JSON")
	}

	private MicrosoftAuthServiceImpl.Endpoints endpoints() {
		def base = "http://127.0.0.1:${server.port()}"
		return new MicrosoftAuthServiceImpl.Endpoints(
				URI.create("$base/device-code"),
				URI.create("$base/token"),
				URI.create("$base/xbox-user"),
				URI.create("$base/xsts"),
				URI.create("$base/minecraft-login"),
				URI.create("$base/entitlements"),
				URI.create("$base/profile")
				)
	}

	private static String xboxToken(String token, String userHash) {
		return """{
			"Token": "$token",
			"DisplayClaims": {
				"xui": [{"uhs": "$userHash"}]
			}
		}"""
	}

	private static void json(Context context, Object value) {
		context.contentType("application/json").result(JsonOutput.toJson(value))
	}
}
