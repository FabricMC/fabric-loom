/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.ide.DefaultRunConfigurationSettings;
import net.fabricmc.loom.task.launch.auth.MicrosoftAccountStore;
import net.fabricmc.loom.task.launch.auth.MicrosoftGameAuthentication;
import net.fabricmc.loom.task.launch.auth.MinecraftAccessTokenProviderImpl;
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStoreFactory;

@DisableCachingByDefault
public abstract class RunGameTask extends AbstractRunTask {
	@Input
	public abstract Property<Boolean> getMicrosoftAuthenticationEnabled();
	@Internal
	protected abstract RegularFileProperty getMicrosoftAccountFile();

	@Inject
	public RunGameTask(RunConfiguration settings) {
		super(proj -> DefaultRunConfigurationSettings.finialise(settings, proj));
		getMicrosoftAuthenticationEnabled().convention(false);
		LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		getMicrosoftAccountFile().fileValue(MicrosoftAccountStore.defaultPath(extension.getFiles()).toFile());

		// Defaults to empty, forwards stdin to mc.
		setStandardInput(System.in);
	}

	@Override
	public void exec() {
		if (getMicrosoftAuthenticationEnabled().get()) {
			addMicrosoftAuthenticationArguments();
		}

		super.exec();
	}

	private void addMicrosoftAuthenticationArguments() {
		Path accountPath = getMicrosoftAccountFile().get().getAsFile().toPath();

		if (!Files.exists(accountPath)) {
			return;
		}

		try {
			MicrosoftAccountStore accountStore = new MicrosoftAccountStore(accountPath, EncryptionKeyStoreFactory.create(accountPath));
			MicrosoftGameAuthentication authentication = new MicrosoftGameAuthentication(accountStore, new MinecraftAccessTokenProviderImpl(), getLogger());
			args(authentication.getLaunchArguments());
		} catch (Exception e) {
			getLogger().error("Failed to initialize Microsoft authentication; starting Minecraft without Microsoft authentication", e);
		}
	}
}
