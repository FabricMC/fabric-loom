package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class LegacyLoomPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		project.getLogger().warn(String.format("Project \"%s\" is using the legacy plugin id for loom.", project.getName()));
		project.getLogger().warn("The plugin id should change from `fabric-loom` to `net.fabricmc.gradle.loom`");
		project.getLogger().warn("For Groovy DSL, change `id \"fabric-loom\"` to `id \"net.fabricmc.gradle.loom\"`");
		project.getLogger().warn("For Kotlin DSL, change `id(\"fabric-loom\")` to `id(\"net.fabricmc.gradle.loom\")`");

		// Apply new plugin
		project.getPlugins().apply("net.fabricmc.gradle.loom");
	}
}
