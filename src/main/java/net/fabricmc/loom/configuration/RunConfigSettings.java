package net.fabricmc.loom.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public class RunConfigSettings implements Named {
	private final List<String> vmArgs = new ArrayList<>();
	private final List<String> programArgs = new ArrayList<>();
	private String mode;
	private String name;
	private Boolean client;
	private final String baseName;
	private Function<Project, SourceSet> source;

	public RunConfigSettings(String baseName) {
		this.baseName = baseName;
		setMode(baseName);
		source("main");
	}

	@Override
	public String getName() {
		return baseName;
	}

	public List<String> getVmArgs() {
		return vmArgs;
	}

	public List<String> getProgramArgs() {
		return programArgs;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getConfigName() {
		return name;
	}

	public void setConfigName(String name) {
		this.name = name;
	}

	public boolean isClient() {
		String m = mode != null ? mode : baseName;
		return client != null ? client // Do not confuse users: detect client mode unless client mode is explicitly defined
				: m.toLowerCase().contains("client");
	}

	public void setClient(Boolean client) {
		this.client = client;
	}

	public SourceSet getSource(Project proj) {
		return source.apply(proj);
	}

	public void setSource(SourceSet source) {
		this.source = proj -> source;
	}

	public void setSource(Function<Project, SourceSet> sourceFn) {
		this.source = sourceFn;
	}

	public void mode(String mode) {
		setMode(mode);
	}

	public void name(String name) {
		setConfigName(name);
	}

	public void client(boolean client) {
		setClient(client);
	}

	public void server(boolean server) {
		setClient(!server);
	}

	public void vmArg(String arg) {
		vmArgs.add(arg);
	}

	public void vmArgs(String... args) {
		vmArgs.addAll(Arrays.asList(args));
	}

	public void vmArgs(Collection<String> args) {
		vmArgs.addAll(args);
	}

	public void property(String name, String value) {
		vmArg("-D" + name + "=" + value);
	}

	public void property(String name) {
		vmArg("-D" + name);
	}

	public void properties(Map<String, String> props) {
		props.forEach(this::property);
	}

	public void programArg(String arg) {
		programArgs.add(arg);
	}

	public void programArgs(String... args) {
		programArgs.addAll(Arrays.asList(args));
	}

	public void programArgs(Collection<String> args) {
		programArgs.addAll(args);
	}

	public void source(SourceSet source) {
		setSource(source);
	}

	public void source(String source) {
		setSource(proj -> {
			JavaPluginConvention conv = proj.getConvention().getPlugin(JavaPluginConvention.class);
			return conv.getSourceSets().getByName(source);
		});
	}
}
