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

package net.fabricmc.loom.util.github;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.util.problem.Problem;

public final class GithubActionsAnnotations {
	// See https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#setting-a-notice-message
	private static final String ERROR_COMMAND = "error";
	private static final String WARNING_COMMAND = "warning";
	private static final String NOTICE_COMMAND = "notice";

	private GithubActionsAnnotations() {
	}

	public static Builder error(String message) {
		return new Builder(ERROR_COMMAND, message);
	}

	public static Builder warning(String message) {
		return new Builder(WARNING_COMMAND, message);
	}

	public static Builder notice(String message) {
		return new Builder(NOTICE_COMMAND, message);
	}

	public static final class Builder {
		private final String command;
		private final String message;
		private @Nullable String file;
		private @Nullable String title;
		private int startLine = 1;
		private int endLine;
		private int startColumn;
		private int endColumn;

		private Builder(String command, String message) {
			this.command = command;
			this.message = message;
		}

		public Builder file(String file) {
			this.file = file;
			return this;
		}

		public Builder file(File file) {
			return file(file.getAbsolutePath());
		}

		public Builder file(Path file) {
			return file(file.toAbsolutePath().toString());
		}

		public Builder file(Problem.FileLocation fileLocation) {
			file(fileLocation.file());

			if (fileLocation.startLine() > 0) {
				line(fileLocation.startLine(), fileLocation.endLine());
			}

			if (fileLocation.startColumn() > 0) {
				column(fileLocation.startColumn(), fileLocation.endColumn());
			}

			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder line(int line) {
			this.startLine = line;
			return this;
		}

		public Builder line(int start, int end) {
			this.startLine = start;
			this.endLine = end;
			return this;
		}

		public Builder column(int column) {
			this.startColumn = column;
			return this;
		}

		public Builder column(int start, int end) {
			this.startColumn = start;
			this.endColumn = end;
			return this;
		}

		public GithubActionsCommand build() {
			Map<String, @Nullable Object> properties = new LinkedHashMap<>();

			if (file != null) {
				properties.put("file", file);
				properties.put("line", startLine);

				if (endLine != 0) {
					properties.put("endLine", endLine);
				}

				if (startColumn != 0) {
					properties.put("col", startColumn);
				}

				if (endColumn != 0) {
					properties.put("endColumn", endColumn);
				}
			}

			if (title != null) {
				properties.put("title", title);
			}

			return new GithubActionsCommand(command, message, properties);
		}
	}
}
