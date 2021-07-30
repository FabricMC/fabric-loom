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
