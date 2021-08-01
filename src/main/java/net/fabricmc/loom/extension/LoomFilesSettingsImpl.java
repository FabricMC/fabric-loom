/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.extension;

import java.io.File;
import java.util.Objects;

import org.gradle.api.initialization.Settings;

import net.fabricmc.loom.configuration.providers.MinecraftProvider;

public class LoomFilesSettingsImpl extends LoomFilesBaseImpl {
	private final Settings settings;

	public LoomFilesSettingsImpl(Settings settings) {
		this.settings = Objects.requireNonNull(settings);
	}

	@Override
	public boolean hasCustomNatives() {
		return false;
	}

	@Override
	public File getNativesDirectory(MinecraftProvider minecraftProvider) {
		throw new IllegalStateException("You can not access natives directory from setting stage");
	}

	@Override
	protected File getGradleUserHomeDir() {
		return settings.getGradle().getGradleUserHomeDir();
	}

	@Override
	protected File getRootDir() {
		return settings.getRootDir();
	}

	@Override
	protected File getProjectDir() {
		throw new IllegalStateException("You can not access project directory from setting stage");
	}

	@Override
	protected File getBuildDir() {
		throw new IllegalStateException("You can not access project build directory from setting stage");
	}
}
