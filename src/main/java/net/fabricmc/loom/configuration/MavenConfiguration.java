package net.fabricmc.loom.configuration;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;

public class MavenConfiguration {
	public static void setup(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		project.getRepositories().flatDir(repo -> {
			repo.setName("UserLocalCacheFiles");
			repo.dir(extension.getRootProjectBuildCache());
		});

		project.getRepositories().maven(repo -> {
			repo.setName("UserLocalRemappedMods");
			repo.setUrl(extension.getRemappedModCache());
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl("https://maven.fabricmc.net/");
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl("https://libraries.minecraft.net/");
		});

		project.getRepositories().mavenCentral();
	}
}
