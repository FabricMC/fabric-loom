package net.fabricmc.loom.api.processors;

import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.task.RemapJarTask;

public interface JarProcessor {
	/**
	 * Checks if the processed file is up to date.
	 * @param project This project
	 * @param path The jar which was processed last
	 * @return Is this processor happy with this (potentially unprocessed) processed jar
	 */
	boolean isUpToDate(Project project, Path path);

	/**
	 * Process the Minecraft jar.
	 * @param project The project
	 * @param from The jar. Can be the original Minecraft jar or the output of the previous processor
	 * @param to The destination to write the jar to
	 * @return Should this processor be skipped
	 * @throws IOException If any {@link IOException exceptions} happen while processing
	 */
	boolean processInput(Project project, Path from, Path to) throws IOException;

	/**
	 * Processes the jar produced by {@link net.fabricmc.loom.task.RemapJarTask}.
	 * @param project The project
	 * @param remapper The ASM remapper being used
	 * @param jar The jar to (optionally) process
	 */
	void processRemapped(Project project, RemapJarTask task, Remapper remapper, Path jar) throws IOException;
}
