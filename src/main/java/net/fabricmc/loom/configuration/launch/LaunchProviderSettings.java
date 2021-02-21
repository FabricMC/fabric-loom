package net.fabricmc.loom.configuration.launch;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.gradle.api.Named;
import org.gradle.api.Project;

public class LaunchProviderSettings implements Named {
	private final String name;
	private List<Map.Entry<String, String>> properties = new ArrayList<>();
	private List<String> arguments = new ArrayList<>();

	public LaunchProviderSettings(Project project, String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	public void arg(String argument) {
		this.arguments.add(argument);
	}

	public void arg(String... arguments) {
		this.arguments.addAll(Arrays.asList(arguments));
	}

	public void arg(Collection<String> arguments) {
		this.arguments.addAll(arguments);
	}

	public void property(String key, String value) {
		this.properties.add(new AbstractMap.SimpleEntry<>(key, value));
	}

	public void properties(Map<String, String> arguments) {
		this.properties.addAll(arguments.entrySet());
	}

	public List<Map.Entry<String, String>> getProperties() {
		return properties;
	}

	public List<String> getArguments() {
		return arguments;
	}
}
