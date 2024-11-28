/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.nativeplatform.LoomNativePlatform;
import net.fabricmc.loom.nativeplatform.LoomNativePlatformException;

public record ProcessUtil(ArgumentVisibility argumentVisibility) {
	private static final String EXPLORER_COMMAND = "C:\\Windows\\explorer.exe";
	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessUtil.class);

	public static ProcessUtil create(Project project) {
		return create(ArgumentVisibility.get(project));
	}

	public static ProcessUtil create(ArgumentVisibility argumentVisibility) {
		return new ProcessUtil(argumentVisibility);
	}

	public String printWithParents(ProcessHandle handle) {
		String result = printWithParents(handle, 0).trim();

		if (argumentVisibility == ArgumentVisibility.HIDE) {
			return "Run with --info or --debug to show arguments, may reveal sensitive info\n" + result;
		}

		return result;
	}

	private String printWithParents(ProcessHandle handle, int depth) {
		var lines = new ArrayList<String>();
		getWindowTitles(handle).ifPresent(titles -> lines.add("title: " + titles));
		lines.add("pid: " + handle.pid());
		handle.info().command().ifPresent(command -> lines.add("command: " + command));
		getProcessArguments(handle).ifPresent(arguments -> lines.add("arguments: " + arguments));
		handle.info().startInstant().ifPresent(instant -> lines.add("started at: " + instant));
		handle.info().user().ifPresent(user -> lines.add("user: " + user));
		handle.parent().ifPresent(parent -> lines.add("parent:\n" + printWithParents(parent, depth + 1)));

		StringBuilder sj = new StringBuilder();

		for (String line : lines) {
			sj.append("\t".repeat(depth)).append("- ").append(line).append('\n');
		}

		return sj.toString();
	}

	private Optional<String> getProcessArguments(ProcessHandle handle) {
		if (argumentVisibility != ArgumentVisibility.SHOW_SENSITIVE) {
			return Optional.empty();
		}

		return handle.info().arguments().map(arr -> {
			String join = String.join(" ", arr);

			if (join.isBlank()) {
				return "";
			}

			return " " + join;
		});
	}

	private Optional<String> getWindowTitles(ProcessHandle processHandle) {
		if (processHandle.info().command().orElse("").equals(EXPLORER_COMMAND)) {
			// Explorer is a single process, so the window titles are not useful
			return Optional.empty();
		}

		List<String> titles;

		try {
			titles = LoomNativePlatform.getWindowTitlesForPid(processHandle.pid());
		} catch (LoomNativePlatformException e) {
			LOGGER.error("{}, Failed to query window title for pid {}", e.getMessage(), processHandle.pid());
			return Optional.empty();
		}

		if (titles.isEmpty()) {
			return Optional.empty();
		}

		final StringJoiner joiner = new StringJoiner(", ");

		for (String title : titles) {
			joiner.add("'" + title + "'");
		}

		return Optional.of(joiner.toString());
	}

	public enum ArgumentVisibility {
		HIDE,
		SHOW_SENSITIVE;

		static ArgumentVisibility get(Project project) {
			final LogLevel logLevel = project.getGradle().getStartParameter().getLogLevel();
			return (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) ? SHOW_SENSITIVE : HIDE;
		}
	}
}
