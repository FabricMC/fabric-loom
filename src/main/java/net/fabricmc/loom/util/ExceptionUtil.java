/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2024 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiFunction;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.nativeplatform.LoomNativePlatform;
import net.fabricmc.loom.nativeplatform.LoomNativePlatformException;
import net.fabricmc.loom.util.gradle.daemon.DaemonUtils;

public final class ExceptionUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionUtil.class);

	/**
	 * Creates a descriptive user-facing wrapper exception for an underlying cause.
	 *
	 * <p>The output format has a message like this: {@code [message], [cause class]: [cause message]}.
	 * For example: {@code Failed to remap, java.io.IOException: Access denied}.
	 *
	 * @param constructor the exception factory which takes in a message and a cause
	 * @param message     the more general message for the resulting exception
	 * @param cause       the causing exception
	 * @param <E> the created exception type
	 * @param <C> the cause type
	 * @return the created exception
	 */
	public static <E, C extends Throwable> E createDescriptiveWrapper(BiFunction<String, C, E> constructor, String message, C cause) {
		String descriptiveMessage = "%s, %s: %s".formatted(message, cause.getClass().getName(), cause.getMessage());
		return constructor.apply(descriptiveMessage, cause);
	}

	public static void processException(Throwable e, Project project) {
		Throwable cause = e;
		boolean unrecoverable = false;

		while (cause != null) {
			if (cause instanceof FileSystemUtil.UnrecoverableZipException) {
				unrecoverable = true;
			} else if (cause instanceof FileSystemException fse) {
				printFileLocks(fse.getFile(), project);
				break;
			}

			cause = cause.getCause();
		}

		if (unrecoverable) {
			DaemonUtils.tryStopGradleDaemon(project);
		}
	}

	private static void printFileLocks(String filename, Project project) {
		final Path path = Paths.get(filename);

		if (!Files.exists(path)) {
			return;
		}

		final List<ProcessHandle> processes;

		try {
			processes = LoomNativePlatform.getProcessesWithLockOn(path);
		} catch (LoomNativePlatformException e) {
			LOGGER.error("{}, Failed to query processes holding a lock on {}", e.getMessage(), path);
			return;
		}

		if (processes.isEmpty()) {
			return;
		}

		final ProcessUtil processUtil = ProcessUtil.create(project);

		final String noun = processes.size() == 1 ? "process has" : "processes have";
		project.getLogger().error("The following {} a lock on the file '{}':", noun, path);

		for (ProcessHandle process : processes) {
			project.getLogger().error(processUtil.printWithParents(process));
		}
	}
}
