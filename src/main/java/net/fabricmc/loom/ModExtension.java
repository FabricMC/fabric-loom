package net.fabricmc.loom;

import org.gradle.api.Project;

import java.util.HashMap;
import java.util.Map;

public class ModExtension {
	private final Project project;
	private HashMap<String, String> replacements = new HashMap<>();

	public ModExtension(Project project) {
		this.project = project;
	}

	public void replace(String key, String value) {
		replacements.put(key, value);
	}

	public void replace(Map<String, String> replacements) {
		this.replacements.putAll(replacements);
	}

	public Map<String, String> getReplacements() {
		return replacements;
	}

	public boolean getEnabled() {
		return !replacements.isEmpty();
	}
}
