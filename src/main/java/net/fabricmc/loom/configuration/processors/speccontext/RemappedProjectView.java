package net.fabricmc.loom.configuration.processors.speccontext;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.api.RemapConfigurationSettings;

public interface RemappedProjectView extends ProjectView {
	Function<RemapConfigurationSettings, Stream<Path>> resolveArtifacts(ArtifactUsage artifactUsage);

	NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations();

	List<RemapConfigurationSettings> getCompileRemapConfigurations();

	List<RemapConfigurationSettings> getRuntimeRemapConfigurations();

	class Impl extends AbstractProjectView implements RemappedProjectView {
		public Impl(Project project) {
			super(project);
		}

		@Override
		public Function<RemapConfigurationSettings, Stream<Path>> resolveArtifacts(ArtifactUsage artifactUsage) {
			final Usage usage = project.getObjects().named(Usage.class, artifactUsage.getGradleUsage());

			return settings -> {
				final Configuration configuration = settings.getSourceConfiguration().get().copyRecursive();
				configuration.setCanBeConsumed(false);
				configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));
				return configuration.resolve().stream().map(File::toPath);
			};
		}

		@Override
		public NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations() {
			return extension.getRemapConfigurations();
		}

		@Override
		public List<RemapConfigurationSettings> getCompileRemapConfigurations() {
			return extension.getCompileRemapConfigurations();
		}

		@Override
		public List<RemapConfigurationSettings> getRuntimeRemapConfigurations() {
			return extension.getRuntimeRemapConfigurations();
		}
	}
}
