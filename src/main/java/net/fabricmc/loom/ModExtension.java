package net.fabricmc.loom;

import org.gradle.api.Project;

public class ModExtension {
	private final Project project;
	private String id = null;
	private String version = null;
	// TODO: add more properties
	// TODO: add dependencies

	public ModExtension(Project project) {
		this.project = project;
	}

	public String getId() {
		return id;
	}

	public String getVersion() {
		return version;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean getEnabled() {
		return id != null || version != null;
	}
}
