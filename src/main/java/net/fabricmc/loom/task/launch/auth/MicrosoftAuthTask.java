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

import java.nio.file.Path;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStoreFactory;

/// Base task for operations on the globally stored Microsoft account.
@DisableCachingByDefault
public abstract class MicrosoftAuthTask extends AbstractLoomTask {
	@ServiceReference(SyncTaskBuildService.NAME)
	abstract Property<SyncTaskBuildService> getSyncTask();

	@Internal
	protected abstract RegularFileProperty getAccountFile();

	@Input
	@Optional
	@Option(option = "profile", description = "Use a named Microsoft authentication profile")
	public abstract Property<String> getProfile();

	public MicrosoftAuthTask() {
		getAccountFile().fileValue(MicrosoftAccountStore.defaultPath(LoomFiles.create(getProject())).toFile());
	}

	@Internal
	protected Path getAccountPath() {
		Path defaultPath = getAccountFile().get().getAsFile().toPath();
		return getProfile().isPresent() ? MicrosoftAccountStore.profilePath(defaultPath, getProfile().get()) : defaultPath;
	}

	protected MicrosoftAccountStore createAccountStore(Path accountPath) {
		return new MicrosoftAccountStore(accountPath, EncryptionKeyStoreFactory.create(accountPath));
	}
}
