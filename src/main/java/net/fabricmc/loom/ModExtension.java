package net.fabricmc.loom;

import org.gradle.api.Project;

import java.io.File;

public class ModExtension {
    private final Project project;
    private String id = null;
    private String version = null;
    // TODO: add more properties
    // TODO: add dependencies

    public ModExtension(Project project) {
        this.project = project;
    }

	public File getResourceFolder() {
		File resources = new File(project.getBuildDir(), "fabric-loom" + File.separator + "resources");
		if (!resources.exists()) {
			resources.mkdirs();
		}
		return resources;
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
}
