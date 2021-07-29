package net.fabricmc.loom.configuration;

import java.util.Map;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.plugins.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;

public final class SoftwareComponentConfiguration {
	private static final Map<String, String> CONFIGURATIONS_TO_PARENTS = Map.of(
			Constants.Configurations.MOD_API_ELEMENTS, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME,
			Constants.Configurations.MOD_RUNTIME_ELEMENTS, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
	);
	private static final Map<String, String> CONFIGURATIONS_TO_SCOPES = Map.of(
			Constants.Configurations.MOD_API_ELEMENTS, "compile",
			Constants.Configurations.MOD_RUNTIME_ELEMENTS, "runtime"
	);
	// A map of mod*Elements -> vanilla Gradle configurations used in the corresponding *Elements.
	// It's done this way to stop the dev jar from leaking into the classpath.
	private static final Multimap<String, String> CONFIGURATIONS_TO_SOURCES = ImmutableMultimap.<String, String>builder()
			.putAll(Constants.Configurations.MOD_API_ELEMENTS, JavaPlugin.API_CONFIGURATION_NAME, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)
			.putAll(Constants.Configurations.MOD_RUNTIME_ELEMENTS, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)
			.build();

	public static void setup(Project project) {
		var extension = LoomGradleExtension.get(project);

		// if you don't remap archives, you can use components.java
		if (extension.getRemapArchives().get()) {
			var configurations = project.getConfigurations();

			CONFIGURATIONS_TO_PARENTS.forEach((configurationName, parentName) -> {
				configurations.getByName(configurationName, configuration -> {
					Configuration parent = configurations.getByName(parentName);
					copyAttributes(parent.getAttributes(), configuration.getAttributes());
				});
			});

			CONFIGURATIONS_TO_SOURCES.forEach((configurationName, sourceConfigurationName) -> {
				configurations.getByName(configurationName).extendsFrom(configurations.getByName(sourceConfigurationName));
			});

			// retrieve SoftwareComponentFactory via injecting it
			var component = extension.getSoftwareComponent();

			CONFIGURATIONS_TO_SCOPES.forEach((name, scope) -> {
				Configuration configuration = configurations.getByName(name);
				component.addVariantsFromConfiguration(configuration, details -> details.mapToMavenScope(scope));
			});

			// because sourcesElements is created too late, we have to set its attributes manually
			configurations.getByName(Constants.Configurations.MOD_SOURCES_ELEMENTS, configuration -> {
				configuration.attributes(attributes -> {
					attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
					attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
					attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, DocsType.SOURCES));
					attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
				});
				component.addVariantsFromConfiguration(configuration, details -> {
					// matches sourcesElements' behaviour
					details.mapToMavenScope("runtime");
					details.mapToOptional();
				});
			});

			// set up other commonly wanted variants that cannot leak dev jars
			project.afterEvaluate(p -> {
				addDefaultConfiguration(configurations, component, JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME);
			});
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void copyAttributes(AttributeContainer from, AttributeContainer to) {
		for (Attribute attribute : from.keySet()) {
			to.attribute(attribute, from.getAttribute(attribute));
		}
	}

	private static void addDefaultConfiguration(ConfigurationContainer configurations, AdhocComponentWithVariants component, String name) {
		@Nullable var configuration = configurations.findByName(name);

		if (configuration != null) {
			component.addVariantsFromConfiguration(configuration, details -> {});
		}
	}
}
