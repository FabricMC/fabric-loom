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
	public static ProcessUtil create(Project project) {
		return new ProcessUtil(project.getGradle().getStartParameter().getLogLevel());
	}

	public String printWithParents(ProcessHandle processHandle) {
		var output = new StringBuilder();

		List<ProcessHandle> chain = getParentChain(null, processHandle);

		for (int i = 0; i < chain.size(); i++) {
			ProcessHandle handle = chain.get(i);

			output.append("\t".repeat(i));

			if (i != 0) {
				output.append("└─ ");
			}

			output.append(getInfoString(handle));

			if (i < chain.size() - 1) {
				output.append('\n');
			}
		}

		return output.toString();
	}

	private String getInfoString(ProcessHandle handle) {
		return "(%s) pid %s '%s%s'%s%s".formatted(
				handle.info().user().orElse("unknown user"),
				handle.pid(),
				handle.info().command().orElse("unknown command"),
				handle.info().arguments().map(arr -> {
					if (logLevel != LogLevel.INFO && logLevel != LogLevel.DEBUG) {
						return " (run with --info or --debug to show arguments, may reveal sensitive info)";
					}

					String join = String.join(" ", arr);

					if (join.isBlank()) {
						return "";
					}

					return " " + join;
				}).orElse(""),
				getWindowTitles(handle),
				handle.info().startInstant().map(instant -> " started at " + instant).orElse("")
		);
	}

	private List<ProcessHandle> getParentChain(List<ProcessHandle> collectTo, ProcessHandle processHandle) {
		if (collectTo == null) {
			collectTo = new ArrayList<>();
		}

		Optional<ProcessHandle> parent = processHandle.parent();

		if (parent.isPresent()) {
			getParentChain(collectTo, parent.get());
		}

		collectTo.add(processHandle);

		return collectTo;
	}

	private String getWindowTitles(ProcessHandle processHandle) {
		List<String> titles = LoomNativePlatform.getWindowTitlesForPid(processHandle.pid());

		if (titles.isEmpty()) {
			return "";
		}

		final StringJoiner joiner = new StringJoiner(", ");

		for (String title : titles) {
			joiner.add("'" + title + "'");
		}

		return joiner.toString();
	}
}
