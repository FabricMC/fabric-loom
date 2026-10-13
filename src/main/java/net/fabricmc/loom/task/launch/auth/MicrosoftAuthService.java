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
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// The remote calls required to exchange a Microsoft login for a Minecraft access token.
///
/// This interface deliberately does not implement device-code polling. Callers should use the
/// interval and expiry supplied by [DeviceCode] and handle the transient errors returned by
/// [#requestMicrosoftToken(String, String)].
///
/// @see [Microsoft identity platform device authorization grant](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)
/// @see [Xbox services authentication](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-xbox-live-authentication)
public interface MicrosoftAuthService {
	/// Starts the OAuth device authorization flow. The returned user code and verification URI
	/// should be shown to the user before token polling begins.
	///
	/// @see [Device authorization request](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code#device-authorization-request)
	DeviceCode requestDeviceCode(String clientId) throws IOException;

	/// Polls Microsoft for the result of a device authorization request. Pending and slow-down
	/// responses are returned as [MicrosoftToken] values rather than thrown as errors.
	///
	/// @see [Device token polling](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code#authenticating-the-user)
	MicrosoftToken requestMicrosoftToken(String clientId, String deviceCode) throws IOException;

	/// Exchanges a refresh token for new Microsoft access and refresh tokens. Callers should replace
	/// the stored refresh token with the one in the returned result.
	///
	/// @see [Refresh the access token](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow#refresh-the-access-token)
	MicrosoftToken.Success refreshMicrosoftToken(String clientId, String refreshToken) throws IOException;

	/// Exchanges the Microsoft access token for an Xbox user token (UToken).
	///
	/// @see [Xbox authorization process](https://learn.microsoft.com/en-us/gaming/gdk/docs/reference/live/rest/additional/edsauthorization)
	XboxToken authenticateXboxUser(String microsoftAccessToken) throws IOException;

	/// Exchanges an Xbox user token for an XSTS token scoped to the Minecraft services relying party.
	///
	/// @see [Xbox services security tokens](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/security-tokens/live-security-tokens)
	XboxToken authorizeMinecraftServices(XboxToken xboxUserToken) throws IOException;

	/// Exchanges the Minecraft-scoped XSTS token for a Minecraft access token.
	MinecraftToken loginToMinecraft(String userHash, String xstsToken) throws IOException;

	/// Checks whether the authenticated account owns Minecraft or can currently play it through
	/// another entitlement such as Game Pass.
	MinecraftEntitlements fetchMinecraftEntitlements(String minecraftAccessToken) throws IOException;

	/// Fetches the Java Edition profile belonging to a Minecraft access token.
	/// An empty result means that the account does not have a Java Edition profile.
	Optional<MinecraftProfile> fetchMinecraftProfile(String minecraftAccessToken) throws IOException;

	/// Values used to present and schedule a device authorization attempt.
	record DeviceCode(String deviceCode, String userCode, URI verificationUri, int expiresIn, int interval, String message) {
		public DeviceCode {
			Objects.requireNonNull(deviceCode, "deviceCode");
			Objects.requireNonNull(userCode, "userCode");
			Objects.requireNonNull(verificationUri, "verificationUri");
			Objects.requireNonNull(message, "message");

			if (expiresIn <= 0) {
				throw new IllegalArgumentException("expiresIn must be positive");
			}

			if (interval <= 0) {
				throw new IllegalArgumentException("interval must be positive");
			}
		}
	}

	/// The result of polling Microsoft for a device authorization.
	sealed interface MicrosoftToken {
		/// Tokens issued after the user completes authorization.
		record Success(String accessToken, String refreshToken, String tokenType, int expiresIn) implements MicrosoftToken {
			public Success {
				Objects.requireNonNull(accessToken, "accessToken");
				Objects.requireNonNull(refreshToken, "refreshToken");
				Objects.requireNonNull(tokenType, "tokenType");

				if (expiresIn <= 0) {
					throw new IllegalArgumentException("expiresIn must be positive");
				}
			}
		}

		/// A response indicating that the caller should continue polling.
		record Polling(Status status, @Nullable String description) implements MicrosoftToken {
			public Polling {
				Objects.requireNonNull(status, "status");
			}

			public enum Status {
				AUTHORIZATION_PENDING,
				SLOW_DOWN
			}
		}
	}

	/// An Xbox user or XSTS token and its associated user hash.
	record XboxToken(String token, String userHash) {
		public XboxToken {
			Objects.requireNonNull(token, "token");
			Objects.requireNonNull(userHash, "userHash");
		}
	}

	/// A bearer token accepted by Minecraft services.
	record MinecraftToken(String accessToken, String tokenType, int expiresIn) {
		public MinecraftToken {
			Objects.requireNonNull(accessToken, "accessToken");
			Objects.requireNonNull(tokenType, "tokenType");

			if (expiresIn <= 0) {
				throw new IllegalArgumentException("expiresIn must be positive");
			}
		}
	}

	/// The account's Minecraft purchase and play entitlements.
	record MinecraftEntitlements(boolean canPlayMinecraft, boolean ownsMinecraft) {
	}

	/// The authenticated player's Java Edition identity.
	record MinecraftProfile(String id, String name) {
		public MinecraftProfile {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(name, "name");
		}
	}
}
