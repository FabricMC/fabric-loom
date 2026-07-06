/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.util.problem;

import java.nio.file.Path;

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.jspecify.annotations.Nullable;

/**
 * Problem details. This type is converted into Gradle {@link org.gradle.api.problems.Problem} instances,
 * exceptions and GitHub Actions annotations.
 *
 * @param id              the Gradle problem ID
 * @param message         the exception message for this problem
 * @param details         a detailed description, can be null
 * @param severity        the severity level
 * @param contextualLabel the contextual label used as a subsection title in the problem report, can be null
 * @param solution        a user-facing solution message, can be null
 * @param fileLocation    the file where this error originated, can be null
 * @param cause           a throwable cause, can be null
 */
public record Problem(ProblemId id, String message, @Nullable String details, Severity severity, @Nullable String contextualLabel, @Nullable String solution, @Nullable FileLocation fileLocation, @Nullable Throwable cause) {
	public static Builder builder(ProblemId problemId) {
		return new Builder(problemId);
	}

	public static final class Builder {
		private final ProblemId problemId;
		private @Nullable String message;
		private @Nullable String details;
		private Severity severity = Severity.ERROR;
		private @Nullable String contextualLabel;
		private @Nullable String solution;
		private @Nullable FileLocation fileLocation;
		private @Nullable Throwable cause;

		public Builder(ProblemId problemId) {
			this.problemId = problemId;
		}

		public Builder message(String message) {
			this.message = message;
			return this;
		}

		public Builder details(String details) {
			this.details = details;
			return this;
		}

		public Builder severity(Severity severity) {
			this.severity = severity;
			return this;
		}

		public Builder contextualLabel(String contextualLabel) {
			this.contextualLabel = contextualLabel;
			return this;
		}

		public Builder solution(String solution) {
			this.solution = solution;
			return this;
		}

		public Builder fileLocation(Path file) {
			this.fileLocation = new FileLocation(file, 0, 0, 0, 0);
			return this;
		}

		public Builder fileLocation(Path file, int line) {
			this.fileLocation = new FileLocation(file, line, 0, 0, 0);
			return this;
		}

		public Builder fileLocation(Path file, int line, int column) {
			this.fileLocation = new FileLocation(file, line, 0, column, 0);
			return this;
		}

		public Builder fileLocation(Path file, int startLine, int endLine, int startColumn, int endColumn) {
			this.fileLocation = new FileLocation(file, startLine, endLine, startColumn, endColumn);
			return this;
		}

		public Builder cause(@Nullable Throwable cause) {
			this.cause = cause;
			return this;
		}

		public Problem build() {
			final String message;

			if (this.message != null) {
				message = this.message;
			} else if (details != null) {
				message = details;
			} else {
				message = problemId.getDisplayName();
			}

			return new Problem(problemId, message, details, severity, contextualLabel, solution, fileLocation, cause);
		}
	}

	// Lines and columns are missing if they're less than or equal to 0
	public record FileLocation(Path file, int startLine, int endLine, int startColumn, int endColumn) {
	}
}
