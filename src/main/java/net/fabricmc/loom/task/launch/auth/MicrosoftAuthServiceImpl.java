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

package net.fabricmc.loom.task.launch.auth;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

public final class MicrosoftAuthServiceImpl implements MicrosoftAuthService {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final String MICROSOFT_SCOPE = "XboxLive.SignIn XboxLive.offline_access";
	private static final Endpoints DEFAULT_ENDPOINTS = new Endpoints(
			URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"),
			URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"),
			URI.create("https://user.auth.xboxlive.com/user/authenticate"),
			URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"),
			URI.create("https://api.minecraftservices.com/launcher/login"),
			URI.create("https://api.minecraftservices.com/entitlements/license"),
			URI.create("https://api.minecraftservices.com/minecraft/profile")
	);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	private final HttpClient httpClient;
	private final Endpoints endpoints;

	public MicrosoftAuthServiceImpl() {
		this(createHttpClient(), DEFAULT_ENDPOINTS);
	}

	public MicrosoftAuthServiceImpl(Endpoints endpoints) {
		this(createHttpClient(), endpoints);
	}

	public MicrosoftAuthServiceImpl(HttpClient httpClient, Endpoints endpoints) {
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
	}

	private static HttpClient createHttpClient() {
		return HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.proxy(ProxySelector.getDefault())
				.connectTimeout(TIMEOUT)
				.build();
	}

	@Override
	public DeviceCode requestDeviceCode(String clientId) throws IOException {
		DeviceCodeResponse response = postForm(endpoints.deviceCode(), Map.of("client_id", clientId, "scope", MICROSOFT_SCOPE), DeviceCodeResponse.class, false);

		int interval = response.interval() > 0 ? response.interval() : 5;
		return new DeviceCode(response.deviceCode(), response.userCode(), URI.create(response.verificationUri()), response.expiresIn(), interval, response.message());
	}

	@Override
	public MicrosoftToken requestMicrosoftToken(String clientId, String deviceCode) throws IOException {
		MicrosoftTokenResponse response = postForm(endpoints.microsoftToken(), Map.of(
				"client_id", clientId,
				"grant_type", "urn:ietf:params:oauth:grant-type:device_code",
				"device_code", deviceCode
		), MicrosoftTokenResponse.class, true);

		if (response.accessToken != null) {
			if (response.refreshToken == null || response.tokenType == null || response.expiresIn <= 0) {
				throw new IOException("Microsoft token response is missing a required field");
			}

			return new MicrosoftToken.Success(response.accessToken, response.refreshToken, response.tokenType, response.expiresIn);
		}

		return switch (response.error) {
		case "authorization_pending" -> new MicrosoftToken.Polling(MicrosoftToken.Polling.Status.AUTHORIZATION_PENDING, response.errorDescription);
		case "slow_down" -> new MicrosoftToken.Polling(MicrosoftToken.Polling.Status.SLOW_DOWN, response.errorDescription);
		case null -> throw new IOException("Microsoft token response is missing a required field");
		default -> throw new IOException("Microsoft device authorization failed: %s".formatted(
				response.errorDescription != null ? response.errorDescription : response.error));
		};
	}

	@Override
	public MicrosoftToken.Success refreshMicrosoftToken(String clientId, String refreshToken) throws IOException {
		MicrosoftTokenResponse response = postForm(endpoints.microsoftToken(), Map.of(
				"client_id", clientId,
				"grant_type", "refresh_token",
				"refresh_token", refreshToken,
				"scope", MICROSOFT_SCOPE
		), MicrosoftTokenResponse.class, true);

		if (response.error != null) {
			throw new IOException("Microsoft token refresh failed: %s".formatted(
					response.errorDescription != null ? response.errorDescription : response.error));
		}

		if (response.accessToken == null || response.refreshToken == null || response.tokenType == null || response.expiresIn <= 0) {
			throw new IOException("Microsoft token refresh response is missing a required field");
		}

		return new MicrosoftToken.Success(response.accessToken, response.refreshToken, response.tokenType, response.expiresIn);
	}

	@Override
	public XboxToken authenticateXboxUser(String microsoftAccessToken) throws IOException {
		JsonObject properties = new JsonObject();
		properties.addProperty("AuthMethod", "RPS");
		properties.addProperty("SiteName", "user.auth.xboxlive.com");
		properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
		JsonObject request = xboxRequest(properties, "http://auth.xboxlive.com");
		return parseXboxToken(postJson(endpoints.xboxUser(), request, JsonObject.class));
	}

	@Override
	public XboxToken authorizeMinecraftServices(XboxToken xboxUserToken) throws IOException {
		JsonObject properties = new JsonObject();
		properties.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(xboxUserToken.token());
		properties.add("UserTokens", tokens);
		JsonObject request = xboxRequest(properties, "rp://api.minecraftservices.com/");
		XboxToken xstsToken = parseXboxToken(postJson(endpoints.xsts(), request, JsonObject.class));

		if (!xboxUserToken.userHash().equals(xstsToken.userHash())) {
			throw new IOException("Xbox authentication changed the user hash");
		}

		return xstsToken;
	}

	@Override
	public MinecraftToken loginToMinecraft(String userHash, String xstsToken) throws IOException {
		JsonObject request = new JsonObject();
		request.addProperty("xtoken", "XBL3.0 x=" + userHash + ";" + xstsToken);
		request.addProperty("platform", "PC_LAUNCHER");
		MinecraftTokenResponse response = postJson(endpoints.minecraftLogin(), request, MinecraftTokenResponse.class);
		return new MinecraftToken(response.accessToken(), response.tokenType(), response.expiresIn());
	}

	@Override
	public MinecraftEntitlements fetchMinecraftEntitlements(String minecraftAccessToken) throws IOException {
		URI uri = URI.create(endpoints.minecraftEntitlements() + "?requestId=" + UUID.randomUUID());
		HttpRequest request = request(uri)
				.header("Authorization", "Bearer " + minecraftAccessToken)
				.GET()
				.build();
		MinecraftEntitlementsResponse response = send(request, MinecraftEntitlementsResponse.class, false);

		boolean canPlayMinecraft = false;
		boolean ownsMinecraft = false;

		for (MinecraftEntitlementItem item : response.items()) {
			canPlayMinecraft |= "game_minecraft".equals(item.name());
			ownsMinecraft |= "product_minecraft".equals(item.name());
		}

		return new MinecraftEntitlements(canPlayMinecraft, ownsMinecraft);
	}

	@Override
	public Optional<MinecraftProfile> fetchMinecraftProfile(String minecraftAccessToken) throws IOException {
		HttpRequest request = request(endpoints.minecraftProfile())
				.header("Authorization", "Bearer " + minecraftAccessToken)
				.GET()
				.build();
		HttpResponse<String> response = send(request);

		if (response.statusCode() == 404) {
			return Optional.empty();
		}

		checkStatus(request, response, false);
		MinecraftProfile profile = parseResponse(request, response, MinecraftProfile.class);
		return Optional.of(profile);
	}

	private static JsonObject xboxRequest(JsonObject properties, String relyingParty) {
		JsonObject request = new JsonObject();
		request.add("Properties", properties);
		request.addProperty("RelyingParty", relyingParty);
		request.addProperty("TokenType", "JWT");
		return request;
	}

	private XboxToken parseXboxToken(JsonObject response) throws IOException {
		try {
			String token = response.get("Token").getAsString();
			String userHash = response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
			return new XboxToken(token, userHash);
		} catch (RuntimeException e) {
			throw new IOException("Xbox authentication response is missing a required field", e);
		}
	}

	private <T> T postForm(URI uri, Map<String, String> values, Class<T> responseType, boolean allowErrorResponse) throws IOException {
		String body = values.entrySet().stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(Collectors.joining("&"));
		HttpRequest request = request(uri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return send(request, responseType, allowErrorResponse);
	}

	private <T> T postJson(URI uri, JsonObject body, Class<T> responseType) throws IOException {
		HttpRequest request = request(uri)
				.header("Content-Type", "application/json")
				.header("x-xbl-contract-version", "1")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
				.build();
		return send(request, responseType, false);
	}

	private <T> T send(HttpRequest request, Class<T> responseType, boolean allowErrorResponse) throws IOException {
		HttpResponse<String> response = send(request);
		checkStatus(request, response, allowErrorResponse);
		return parseResponse(request, response, responseType);
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException {
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while authenticating with Microsoft", e);
		}
	}

	private static void checkStatus(HttpRequest request, HttpResponse<String> response, boolean allowErrorResponse) throws IOException {
		if (!allowErrorResponse && (response.statusCode() < 200 || response.statusCode() >= 300)) {
			throw new IOException("Authentication request to %s failed with HTTP %d: %s".formatted(request.uri(), response.statusCode(), response.body()));
		}
	}

	private static <T> T parseResponse(HttpRequest request, HttpResponse<String> response, Class<T> responseType) throws IOException {
		try {
			T result = GSON.fromJson(response.body(), responseType);

			if (result == null) {
				throw new IOException("Authentication request to %s returned an empty response".formatted(request.uri()));
			}

			return result;
		} catch (RuntimeException e) {
			throw new IOException("Authentication request to %s returned invalid JSON".formatted(request.uri()), e);
		}
	}

	private static HttpRequest.Builder request(URI uri) {
		return HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("Accept", "application/json");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval, String message) {
		private DeviceCodeResponse {
			Objects.requireNonNull(deviceCode, "deviceCode");
			Objects.requireNonNull(userCode, "userCode");
			Objects.requireNonNull(verificationUri, "verificationUri");
			Objects.requireNonNull(message, "message");

			if (expiresIn <= 0) {
				throw new IllegalArgumentException("expiresIn must be positive");
			}
		}
	}

	private static final class MicrosoftTokenResponse {
		private @Nullable String accessToken;
		private @Nullable String refreshToken;
		private @Nullable String tokenType;
		private int expiresIn;
		private @Nullable String error;
		private @Nullable String errorDescription;
	}

	public record Endpoints(URI deviceCode, URI microsoftToken, URI xboxUser, URI xsts, URI minecraftLogin,
			URI minecraftEntitlements, URI minecraftProfile) {
		public Endpoints {
			Objects.requireNonNull(deviceCode, "deviceCode");
			Objects.requireNonNull(microsoftToken, "microsoftToken");
			Objects.requireNonNull(xboxUser, "xboxUser");
			Objects.requireNonNull(xsts, "xsts");
			Objects.requireNonNull(minecraftLogin, "minecraftLogin");
			Objects.requireNonNull(minecraftEntitlements, "minecraftEntitlements");
			Objects.requireNonNull(minecraftProfile, "minecraftProfile");
		}
	}

	private record MinecraftTokenResponse(String accessToken, String tokenType, int expiresIn) {
		private MinecraftTokenResponse {
			Objects.requireNonNull(accessToken, "accessToken");
			Objects.requireNonNull(tokenType, "tokenType");

			if (expiresIn <= 0) {
				throw new IllegalArgumentException("expiresIn must be positive");
			}
		}
	}

	private record MinecraftEntitlementsResponse(List<MinecraftEntitlementItem> items) {
		private MinecraftEntitlementsResponse {
			items = List.copyOf(items);
		}
	}

	private record MinecraftEntitlementItem(String name) {
		private MinecraftEntitlementItem {
			Objects.requireNonNull(name, "name");
		}
	}
}
