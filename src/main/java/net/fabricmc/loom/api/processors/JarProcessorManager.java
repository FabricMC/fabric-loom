package net.fabricmc.loom.api.processors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.task.RemapJarTask;

public class JarProcessorManager {
	private final Project project;
	private final List<JarProcessor> processors;
	private final Path work;

	public JarProcessorManager(Project project, List<JarProcessor> processors, Path work) {
		this.project = project;
		this.processors = processors;
		this.work = work;
	}

	public boolean process(Path input, Path output) throws IOException {
		if (processors.isEmpty()) {
			return false;
		}

		if (!Files.exists(output) || !processors.stream().allMatch(processor -> processor.isUpToDate(project, output))) {
			project.getLogger().lifecycle(":processing mapped jar");
			Path in = input;

			for (int processorCount = processors.size(), i = 0; i < processorCount; i++) {
				project.getLogger().lifecycle(":processing mapped jar [" + (i + 1) + " of " + processorCount + "]");
				Path out = work.resolve("work-" + i + ".jar");
				Files.deleteIfExists(out);

				if (!processors.get(i).processInput(project, in, out)) {
					in = out;
				}
			}

			Files.createDirectories(output.getParent());
			Files.copy(in, output);
		}

		return true;
	}

	public void processRemapped(RemapJarTask task, Remapper remapper, Path jar) throws IOException {
		for (JarProcessor processor : processors) {
			processor.processRemapped(project, task, remapper, jar);
		}
	}
}
