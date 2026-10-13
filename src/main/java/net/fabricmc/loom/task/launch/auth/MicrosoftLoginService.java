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

/// Performs the interactive Microsoft login that is required when an account is first added.
public interface MicrosoftLoginService {
	/// Starts device authorization, reports the code to the caller, and waits for the user to finish
	/// authentication. The returned refresh token should be stored securely for subsequent launches.
	LoginResult login(String clientId, Consumer<MicrosoftAuthService.DeviceCode> deviceCodeConsumer) throws IOException;

	record LoginResult(String refreshToken, MicrosoftAuthService.MinecraftProfile profile,
			MicrosoftAuthService.MinecraftEntitlements entitlements) {
		public LoginResult {
			Objects.requireNonNull(refreshToken, "refreshToken");
			Objects.requireNonNull(profile, "profile");
			Objects.requireNonNull(entitlements, "entitlements");
		}
	}
}
