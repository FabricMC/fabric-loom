package net.fabricmc.loom.configuration.processors.speccontext;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.gradle.SourceSetHelper;


public interface DeobfProjectView extends ProjectView {
	Configuration getRuntimeClasspath();

	Configuration getCompileClasspath();

	// Null when not split sources
	@Nullable Configuration getRuntimeClientClasspath();

	// Null when not split sources
	@Nullable Configuration getCompileClientClasspath();

	FileCollection getFullClasspath();

	class Impl extends AbstractProjectView implements DeobfProjectView {
		private final ConfigurationContainer configurations;

		protected Impl(Project project) {
			super(project);
			this.configurations = project.getConfigurations();
		}

		@Override
		public Configuration getRuntimeClasspath() {
			return configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
		}

		@Override
		public Configuration getCompileClasspath() {
			// TODO also include `api`
			return configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
		}

		@Override
		public @Nullable Configuration getRuntimeClientClasspath() {
			if (!areEnvironmentSourceSetsSplit()) {
				return null;
			}

			return configurations.getByName(getClientSourceSet().getRuntimeOnlyConfigurationName());
		}

		@Override
		public @Nullable Configuration getCompileClientClasspath() {
			if (!areEnvironmentSourceSetsSplit()) {
				return null;
			}

			// TODO also include `api`
			return configurations.getByName(getClientSourceSet().getImplementationConfigurationName());
		}

		@Override
		public FileCollection getFullClasspath() {
			ConfigurableFileCollection classpath = project.files();

			classpath.from(getRuntimeClasspath());
			classpath.from(getCompileClasspath());

			if (areEnvironmentSourceSetsSplit()) {
				classpath.from(getRuntimeClientClasspath());
				classpath.from(getCompileClientClasspath());
			}

			return classpath;
		}

		private SourceSet getClientSourceSet() {
			return SourceSetHelper.getSourceSetByName("client", project);
		}
	}
}
