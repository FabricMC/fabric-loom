package net.fabricmc.loom.extension;

import java.io.File;
import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.MinecraftProvider;

public final class LoomFilesProjectImpl extends LoomFilesBaseImpl {
	private final Project project;

	public LoomFilesProjectImpl(Project project) {
		this.project = Objects.requireNonNull(project);
	}

	@Override
	protected File getGradleUserHomeDir() {
		return project.getGradle().getGradleUserHomeDir();
	}

	@Override
	protected File getRootDir() {
		return project.getRootDir();
	}

	@Override
	protected File getProjectDir() {
		return project.getProjectDir();
	}

	@Override
	protected File getBuildDir() {
		return project.getBuildDir();
	}

	@Override
	public boolean hasCustomNatives() {
		return project.getProperties().get("fabric.loom.natives.dir") != null;
	}

	@Override
	public File getNativesDirectory(MinecraftProvider minecraftProvider) {
		if (hasCustomNatives()) {
			return new File((String) project.property("fabric.loom.natives.dir"));
		}

		File natives = new File(getUserCache(), "natives/" + minecraftProvider.minecraftVersion());

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}
}
