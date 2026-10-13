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

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

/// Refreshes a stored Microsoft account and produces the arguments for the game.
public final class MicrosoftGameAuthentication {
	private final MicrosoftAccountStore accountStore;
	private final MinecraftAccessTokenProvider accessTokenProvider;
	private final Logger logger;

	public MicrosoftGameAuthentication(MicrosoftAccountStore accountStore, MinecraftAccessTokenProvider accessTokenProvider, Logger logger) {
		this.accountStore = Objects.requireNonNull(accountStore, "accountStore");
		this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public List<String> getLaunchArguments() {
		if (!accountStore.exists()) {
			return List.of();
		}

		try {
			MicrosoftAccountStore.Account account = accountStore.read();
			MinecraftAccessTokenProvider.AccessToken accessToken = accessTokenProvider.getAccessToken(
					account.clientId(),
					account.refreshToken(),
					refreshToken -> storeRefreshToken(account, refreshToken)
			);

			logger.info("Using Microsoft account {} to launch Minecraft", account.profileName());
			return List.of(
					"--username", account.profileName(),
					"--uuid", account.profileId(),
					"--accessToken", accessToken.accessToken(),
					"--userType", "msa"
			);
		} catch (Exception e) {
			logger.error("Failed to authenticate with Microsoft; starting Minecraft without Microsoft authentication", e);
			return List.of();
		}
	}

	private void storeRefreshToken(MicrosoftAccountStore.Account account, String refreshToken) {
		try {
			accountStore.write(account.withRefreshToken(refreshToken));
		} catch (Exception e) {
			logger.error("Failed to store the rotated Microsoft refresh token; continuing authentication for this Minecraft launch", e);
		}
	}
}
