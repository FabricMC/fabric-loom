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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

public final class MicrosoftLoginServiceImpl implements MicrosoftLoginService {
	private final MicrosoftAuthService authService;
	private final Sleeper sleeper;

	public MicrosoftLoginServiceImpl() {
		this(new MicrosoftAuthServiceImpl());
	}

	public MicrosoftLoginServiceImpl(MicrosoftAuthService authService) {
		this(authService, duration -> Thread.sleep(duration));
	}

	public MicrosoftLoginServiceImpl(MicrosoftAuthService authService, Sleeper sleeper) {
		this.authService = Objects.requireNonNull(authService, "authService");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
	}

	@Override
	public LoginResult login(String clientId, Consumer<MicrosoftAuthService.DeviceCode> deviceCodeConsumer) throws IOException {
		MicrosoftAuthService.DeviceCode deviceCode = authService.requestDeviceCode(clientId);
		Objects.requireNonNull(deviceCodeConsumer, "deviceCodeConsumer").accept(deviceCode);
		MicrosoftAuthService.MicrosoftToken.Success microsoftToken = pollForToken(clientId, deviceCode);
		MinecraftAuthFlow.Result minecraft = MinecraftAuthFlow.authenticate(authService, microsoftToken.accessToken());
		MicrosoftAuthService.MinecraftProfile profile = authService.fetchMinecraftProfile(minecraft.token().accessToken())
				.orElseThrow(() -> new IOException("The Microsoft account does not have a Minecraft Java Edition profile"));
		return new LoginResult(microsoftToken.refreshToken(), profile, minecraft.entitlements());
	}

	private MicrosoftAuthService.MicrosoftToken.Success pollForToken(String clientId, MicrosoftAuthService.DeviceCode deviceCode) throws IOException {
		Instant expiresAt = Instant.now().plusSeconds(deviceCode.expiresIn());
		int interval = deviceCode.interval();

		while (Instant.now().isBefore(expiresAt)) {
			if (!Instant.now().plusSeconds(interval).isBefore(expiresAt)) {
				break;
			}

			sleep(Duration.ofSeconds(interval));

			try {
				MicrosoftAuthService.MicrosoftToken result = authService.requestMicrosoftToken(clientId, deviceCode.deviceCode());

				if (result instanceof MicrosoftAuthService.MicrosoftToken.Success success) {
					return success;
				}

				MicrosoftAuthService.MicrosoftToken.Polling polling = (MicrosoftAuthService.MicrosoftToken.Polling) result;

				if (polling.status() == MicrosoftAuthService.MicrosoftToken.Polling.Status.SLOW_DOWN) {
					interval += 5;
				}
			} catch (HttpTimeoutException e) {
				interval *= 2;
			}
		}

		throw new IOException("Microsoft device authorization expired before it completed");
	}

	private void sleep(Duration duration) {
		try {
			sleeper.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for Microsoft authentication", e);
		}
	}

	@FunctionalInterface
	public interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}
}
