package net.fabricmc.loom.bootstrap;

import org.gradle.api.Project;

public interface BootstrappedPlugin {
	void apply(Project project);
}
