/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.WarningMode;

public interface DeprecationHelper {
	default void replaceWithInLoom0_10(String currentName, String newName) {
		toBeRemovedIn(currentName, newName, "Loom 0.10");
	}

	default void replaceWithInLoom0_11(String currentName, String newName) {
		toBeRemovedIn(currentName, newName, "Loom 0.11");
	}

	default void replaceWithInLoom0_12(String currentName, String newName) {
		toBeRemovedIn(currentName, newName, "Loom 0.12");
	}

	default void toBeRemovedIn(String currentName, String newName, String removalVersion) {
		warn("The '%s' property has been deprecated, and has been replaced with '%s'. This is scheduled to be removed in %s.".formatted(currentName, newName, removalVersion));
	}

	Project getProject();

	void warn(String warning);

	class ProjectBased implements DeprecationHelper {
		private final Project project;
		private final AtomicBoolean usingDeprecatedApi = new AtomicBoolean(false);

		public ProjectBased(Project project) {
			this.project = project;

			project.getGradle().buildFinished(buildResult -> {
				if (usingDeprecatedApi.get()) {
					project.getLogger().lifecycle("Deprecated Loom APIs were used in this build, making it incompatible with future versions of Loom. "
											+ "Use Gradle warning modes to control the verbosity of the warnings.");
				}
			});
		}

		@Override
		public Project getProject() {
			return project;
		}

		@Override
		public void warn(String warning) {
			WarningMode warningMode = getProject().getGradle().getStartParameter().getWarningMode();
			getProject().getLogger().log(warningMode == WarningMode.None ? LogLevel.INFO : LogLevel.WARN, warning);

			if (warningMode == WarningMode.Fail) {
				throw new UnsupportedOperationException(warning);
			}

			usingDeprecatedApi.set(true);
		}
	}
}
