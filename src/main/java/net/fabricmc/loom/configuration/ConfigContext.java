package net.fabricmc.loom.configuration;

import net.fabricmc.loom.util.service.SharedServiceManager;

import org.gradle.api.Project;

public interface ConfigContext {
	Project project();
	SharedServiceManager serviceManager();
}
