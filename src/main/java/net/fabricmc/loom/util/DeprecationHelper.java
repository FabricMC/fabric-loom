/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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
	default void replaceWithInLoom2_0(String currentName, String newName) {
		toBeRemovedIn(currentName, newName, "Loom 2.0");
	}

	default void removedInLoom2_0(String currentName) {
		toBeRemovedIn(currentName, "Loom 2.0");
	}

	default void toBeRemovedIn(String currentName, String newName, String removalVersion) {
		warn("The '%s' property has been deprecated, and has been replaced with '%s'. This is scheduled to be removed in %s.".formatted(currentName, newName, removalVersion));
	}

	default void toBeRemovedIn(String currentName, String removalVersion) {
		warn("The '%s' property has been deprecated, and can be removed. This is scheduled to be removed in %s.".formatted(currentName, removalVersion));
	}

	Project getProject();

	void warn(String warning);

	class ProjectBased implements DeprecationHelper {
		private final Project project;
		private final AtomicBoolean usingDeprecatedApi = new AtomicBoolean(false);

		public ProjectBased(Project project) {
			this.project = project;
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
