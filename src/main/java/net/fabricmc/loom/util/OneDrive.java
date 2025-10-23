/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// OneDrive causes all kinds of issues, log a warning if the users project is stored there.
public final class OneDrive {
	private static final List<String> ENV_VARS = List.of("OneDrive", "OneDriveConsumer", "OneDriveCommercial");
	private static final Supplier<List<Path>> ONE_DRIVE_PATHS = Lazy.of(OneDrive::getOneDrivePaths);
	private static final Logger LOGGER = LoggerFactory.getLogger(OneDrive.class);

	private OneDrive() {
	}

	public static void verify(Project project) {
		if (!Platform.CURRENT.getOperatingSystem().isWindows()) {
			return;
		}

		Path projectDir = project.getProjectDir().toPath();

		if (isInOneDrive(projectDir)) {
			LOGGER.warn("Project directory '{}' is located in OneDrive. This is known to cause issues and slower performance. Consider moving the project to a different location.", projectDir);
		}
	}

	private static boolean isInOneDrive(Path path) {
		Path normalized = path.toAbsolutePath().normalize();

		for (Path oneDrivePath : ONE_DRIVE_PATHS.get()) {
			if (normalized.startsWith(oneDrivePath)) {
				return true;
			}
		}

		return false;
	}

	private static List<Path> getOneDrivePaths() {
		var paths = new ArrayList<Path>();

		for (String envVar : ENV_VARS) {
			String value = System.getenv(envVar);

			if (value == null || value.isEmpty()) {
				continue;
			}

			try {
				Path path = Path.of(value);
				paths.add(path.toAbsolutePath().normalize());
			} catch (InvalidPathException ignored) {
				// Don't care
			}
		}

		return Collections.unmodifiableList(paths);
	}
}
