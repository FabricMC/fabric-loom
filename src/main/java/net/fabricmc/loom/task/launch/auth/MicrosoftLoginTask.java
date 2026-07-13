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

import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import net.fabricmc.loom.util.Constants;

/// Authenticates a Microsoft account and stores its refresh token for future client launches.
@UntrackedTask(because = "Microsoft login must always check the global account state.")
public abstract class MicrosoftLoginTask extends MicrosoftAuthTask {
	public MicrosoftLoginTask() {
		setDescription("Log in to Minecraft with a Microsoft account");
	}

	@TaskAction
	public void login() {
		try {
			loginInternal(getAccountPath());
		} catch (GradleException e) {
			throw e;
		} catch (Exception e) {
			String detail = e.getMessage();
			String message = detail == null || detail.isBlank()
					? "Microsoft login failed."
					: "Microsoft login failed: " + detail;
			throw new GradleException(message, e);
		}
	}

	private void loginInternal(Path accountPath) throws Exception {
		MicrosoftAccountStore accountStore = null;
		String clientId = null;

		if (Files.exists(accountPath)) {
			accountStore = createAccountStore(accountPath);

			try {
				MicrosoftAccountStore.Account account = accountStore.read();
				clientId = account.clientId();
				verifyStoredAccount(accountStore, account);
				getLogger().lifecycle("Microsoft account {} is already logged in and ready to launch; skipping login.", account.profileName());
				return;
			} catch (Exception e) {
				getLogger().warn("The stored Microsoft account could not be verified; starting Microsoft login again.");
				getLogger().debug("Stored Microsoft account verification failed", e);
			}
		}

		if (clientId == null) {
			clientId = getClientId();
		}

		if (clientId.isBlank()) {
			throw new GradleException("Microsoft login is not configured. Set Constants.MICROSOFT_CLIENT_ID to the Microsoft application client ID.");
		}

		if (accountStore == null) {
			accountStore = createAccountStore(accountPath);
		}

		accountStore.prepare();

		MicrosoftLoginService.LoginResult result = createLoginService().login(clientId, this::displayDeviceCode);
		MicrosoftAuthService.MinecraftProfile profile = result.profile();
		accountStore.write(new MicrosoftAccountStore.Account(clientId, result.refreshToken(), profile.id(), profile.name()));

		getLogger().lifecycle("Logged in to Minecraft as {} ({}).", profile.name(), profile.id());
	}

	private void verifyStoredAccount(MicrosoftAccountStore accountStore, MicrosoftAccountStore.Account account) throws Exception {
		createAccessTokenProvider().getAccessToken(
				account.clientId(),
				account.refreshToken(),
				refreshToken -> storeRefreshToken(accountStore, account, refreshToken)
		);
	}

	private static void storeRefreshToken(MicrosoftAccountStore accountStore, MicrosoftAccountStore.Account account, String refreshToken) {
		try {
			accountStore.write(account.withRefreshToken(refreshToken));
		} catch (Exception e) {
			throw new RuntimeException("Failed to store the rotated Microsoft refresh token", e);
		}
	}

	@Internal
	protected String getClientId() {
		return Constants.MICROSOFT_CLIENT_ID;
	}

	protected MicrosoftLoginService createLoginService() {
		return new MicrosoftLoginServiceImpl();
	}

	protected MinecraftAccessTokenProvider createAccessTokenProvider() {
		return new MinecraftAccessTokenProviderImpl();
	}

	private void displayDeviceCode(MicrosoftAuthService.DeviceCode deviceCode) {
		getLogger().lifecycle(deviceCode.message());
		getLogger().lifecycle("Verification URI: {}", deviceCode.verificationUri());
		getLogger().lifecycle("User code: {}", deviceCode.userCode());
	}
}
