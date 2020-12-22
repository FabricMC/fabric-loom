package net.fabricmc.loom.configuration.ide;

import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.model.IdeaModel;

public final class IdeConfiguration {
	private IdeConfiguration() {
	}

	public static void setup(Project project) {
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");

		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		ideaModel.getModule().setInheritOutputDirs(true);
	}
}
