package net.fabricmc.loom.task;

import static net.fabricmc.loom.task.GenerateSourcesTask.getMappedJarFileWithSuffix;

import java.nio.file.Files;

import org.gradle.api.tasks.TaskAction;

public class CleanSourcesTask extends AbstractLoomTask {
	@TaskAction
	public void doTask() throws Throwable {
		Files.deleteIfExists(getMappedJarFileWithSuffix(getProject(), "-sources.jar", false).toPath());
		Files.deleteIfExists(getMappedJarFileWithSuffix(getProject(), "-sources.jar", true).toPath());
	}
}
