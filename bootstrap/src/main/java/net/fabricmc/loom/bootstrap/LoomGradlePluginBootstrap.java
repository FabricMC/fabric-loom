package net.fabricmc.loom.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.GradleVersion;

/**
 * This bootstrap is compiled against a minimal gradle API and java 8, this allows us to show a nice error to users who run on unsupported configurations.
 */
@SuppressWarnings("unused")
public class LoomGradlePluginBootstrap implements Plugin<PluginAware> {
	private static final String MIN_SUPPORTED_GRADLE_VERSION = "8.10";
	private static final int MIN_SUPPORTED_MAJOR_JAVA_VERSION = 17;
	private static final int MIN_SUPPORTED_MAJOR_IDEA_VERSION = 2022;

	private static final String PLUGIN_CLASS_NAME = "net.fabricmc.loom.LoomGradlePlugin";
	private static final String IDEA_VERSION_PROP_KEY = "idea.version";

	@Override
	public void apply(PluginAware pluginAware) {
		if (pluginAware instanceof Project) {
			Project project = (Project) pluginAware;

			if (project.findProperty("fabric.loom.skip-env-validation") == null) {
				validateEnvironment();
			} else {
				project.getLogger().lifecycle("Loom environment validation disabled. Please re-enable before reporting any issues.");
			}
		}

		getActivePlugin().apply(pluginAware);
	}

	private void validateEnvironment() {
		List<String> errors = new ArrayList<>();

		if (!isValidGradleRuntime()) {
			errors.add(String.format("You are using an outdated version of Gradle (%s). Gradle %s or higher is required.", GradleVersion.current().getVersion(), MIN_SUPPORTED_GRADLE_VERSION));
		}

		if (!isValidJavaRuntime()) {
			errors.add(String.format("You are using an outdated version of Java (%s). Java %d or higher is required.", JavaVersion.current().getMajorVersion(), MIN_SUPPORTED_MAJOR_JAVA_VERSION));

			if (Boolean.getBoolean("idea.active")) {
				// Idea specific error
				errors.add("You can change the Java version in the Gradle settings dialog.");
			} else {
				String javaHome = System.getenv("JAVA_HOME");

				if (javaHome != null) {
					errors.add(String.format("The JAVA_HOME environment variable is currently set to (%s).", javaHome));
				}
			}
		}

		if (!isValidIdeaRuntime()) {
			errors.add(String.format("You are using an outdated version of intellij idea (%s). Intellij idea %d or higher is required.", System.getProperty(IDEA_VERSION_PROP_KEY), MIN_SUPPORTED_MAJOR_IDEA_VERSION));
		}

		if (!errors.isEmpty()) {
			throw new UnsupportedOperationException(String.join("\n", errors));
		}
	}

	private static boolean isValidJavaRuntime() {
		// Note use compareTo to ensure compatibility with gradle < 6.0
		return JavaVersion.current().compareTo(JavaVersion.toVersion(MIN_SUPPORTED_MAJOR_JAVA_VERSION)) >= 0;
	}

	private static boolean isValidGradleRuntime() {
		return GradleVersion.current().compareTo(GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)) >= 0;
	}

	private static boolean isValidIdeaRuntime() {
		String version = System.getProperty(IDEA_VERSION_PROP_KEY);

		if (version == null) {
			return true;
		}

		int ideaYear = Integer.parseInt(version.substring(0, version.indexOf(".")));
		return ideaYear >= MIN_SUPPORTED_MAJOR_IDEA_VERSION;
	}

	BootstrappedPlugin getActivePlugin() {
		try {
			return (BootstrappedPlugin) Class.forName(PLUGIN_CLASS_NAME).getConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to bootstrap loom", e);
		}
	}
}
