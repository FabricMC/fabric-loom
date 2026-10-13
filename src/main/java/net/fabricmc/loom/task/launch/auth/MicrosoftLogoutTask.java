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
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/// Deletes the locally stored Microsoft account and its platform-protected encryption key.
@UntrackedTask(because = "Microsoft logout must always check the global account state.")
public abstract class MicrosoftLogoutTask extends MicrosoftAuthTask {
	public MicrosoftLogoutTask() {
		setDescription("Log out of the stored Microsoft account");
	}

	@TaskAction
	public void logout() {
		Path accountPath = getAccountPath();
		boolean accountExisted = Files.exists(accountPath);
		MicrosoftAccountStore accountStore;

		try {
			accountStore = createAccountStore(accountPath);
		} catch (UnsupportedOperationException e) {
			deleteAccountOnUnsupportedPlatform(accountPath, accountExisted);
			return;
		}

		try {
			accountStore.delete();

			if (accountExisted) {
				getLogger().lifecycle("Deleted the stored Microsoft account.");
			} else {
				getLogger().lifecycle("No Microsoft account was stored; removed any remaining secure-storage key.");
			}
		} catch (Exception e) {
			throw new GradleException("Microsoft logout failed.", e);
		}
	}

	private void deleteAccountOnUnsupportedPlatform(Path accountPath, boolean accountExisted) {
		try {
			Files.deleteIfExists(accountPath);
		} catch (IOException | SecurityException e) {
			throw new GradleException("Microsoft logout failed.", e);
		}

		if (accountExisted) {
			getLogger().lifecycle("Deleted the stored Microsoft account. Secure platform storage is not supported on this operating system.");
		} else {
			getLogger().lifecycle("No Microsoft account is stored; skipping logout.");
		}
	}
}
