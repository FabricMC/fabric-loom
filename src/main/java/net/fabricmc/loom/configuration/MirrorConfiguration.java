package net.fabricmc.loom.configuration;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Mirrors;

import org.gradle.api.Project;

public class MirrorConfiguration {
	public static void setup(Project project){
		if (!(project.hasProperty("loom_libraries_base")
				|| project.hasProperty("loom_resources_base")
		        || project.hasProperty("version_manifests"))){
			return;
		}

		String LIBRARIES_BASE_MIRRORS = project.hasProperty("loom_libraries_base")
				? String.valueOf(project.property("loom_libraries_base")) : Constants.LIBRARIES_BASE;
		String RESOURCES_BASE_MIRRORS = project.hasProperty("loom_resources_base")
				? String.valueOf(project.property("loom_resources_base")) : Constants.RESOURCES_BASE;
		String VERSION_MANIFESTS = project.hasProperty("version_manifests")
				? String.valueOf(project.property("version_manifests")) : Constants.VERSION_MANIFESTS;

		Mirrors.changeMirror(LIBRARIES_BASE_MIRRORS
				,RESOURCES_BASE_MIRRORS
				,VERSION_MANIFESTS);
	}
}
