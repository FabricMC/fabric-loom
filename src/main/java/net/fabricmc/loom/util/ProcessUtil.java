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

import net.fabricmc.loom.nativeplatform.LoomNativePlatform;

public record ProcessUtil(LogLevel logLevel) {
	private static final String EXPLORER_COMMAND = "C:\\Windows\\explorer.exe";

	public static ProcessUtil create(Project project) {
		return new ProcessUtil(project.getGradle().getStartParameter().getLogLevel());
	}

	public String printWithParents(ProcessHandle handle) {
		String result = printWithParents(handle, 0).trim();

		if (logLevel != LogLevel.INFO && logLevel != LogLevel.DEBUG) {
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
		if (logLevel != LogLevel.INFO && logLevel != LogLevel.DEBUG) {
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

		List<String> titles = LoomNativePlatform.getWindowTitlesForPid(processHandle.pid());

		if (titles.isEmpty()) {
			return Optional.empty();
		}

		final StringJoiner joiner = new StringJoiner(", ");

		for (String title : titles) {
			joiner.add("'" + title + "'");
		}

		return Optional.of(joiner.toString());
	}
}
