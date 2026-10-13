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
import java.util.Objects;
import java.util.function.Consumer;

/// Obtains a fresh Minecraft access token immediately before the game is launched.
public interface MinecraftAccessTokenProvider {
	/// Refreshes the stored Microsoft session and exchanges it for a Minecraft access token.
	/// The consumer is called with the replacement Microsoft refresh token immediately after it is
	/// issued, before the Xbox and Minecraft exchanges are attempted.
	AccessToken getAccessToken(String clientId, String refreshToken, Consumer<String> refreshTokenConsumer) throws IOException;

	record AccessToken(String accessToken, String refreshToken, int expiresIn) {
		public AccessToken {
			Objects.requireNonNull(accessToken, "accessToken");
			Objects.requireNonNull(refreshToken, "refreshToken");

			if (expiresIn <= 0) {
				throw new IllegalArgumentException("expiresIn must be positive");
			}
		}
	}
}
