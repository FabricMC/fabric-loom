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

package net.fabricmc.loom.util.gradle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public class ThreadedProgressLoggerConsumer implements Consumer<String>, AutoCloseable {
	public static final String CLOSE_LOGGERS = "LOOM_CLOSE_LOGGERS";

	private final Logger logger;
	private final String name;
	private final String desc;

	private final ProgressLoggerFactory progressLoggerFactory;
	private final ProgressLogger progressGroup;
	private final Map<String, ProgressLogger> loggers = Collections.synchronizedMap(new HashMap<>());

	public ThreadedProgressLoggerConsumer(Project project, String name, String desc) {
		this.logger = project.getLogger();
		this.name = name;
		this.desc = desc;

		this.progressLoggerFactory = ((ProjectInternal) project).getServices().get(ProgressLoggerFactory.class);
		this.progressGroup = this.progressLoggerFactory.newOperation(name).setDescription(desc);
		progressGroup.started();
	}

	public ThreadedProgressLoggerConsumer(Logger logger, ProgressLoggerFactory progressLoggerFactory, String name, String desc) {
		this.logger = logger;
		this.name = name;
		this.desc = desc;
		this.progressLoggerFactory = progressLoggerFactory;
		this.progressGroup = this.progressLoggerFactory.newOperation(name).setDescription(desc);
		progressGroup.started();
	}

	@Override
	public void accept(String line) {
		if (!line.contains("::")) {
			logger.debug("Malformed threaded IPC log message: " + line);
			return;
		}

		int idx = line.indexOf("::");
		String id = line.substring(0, idx).trim();
		String data = line.substring(idx + 2).trim();

		if (data.equals(CLOSE_LOGGERS)) {
			resetLoggers();
			return;
		}

		loggers.computeIfAbsent(id, this::createLogger).progress(data);
	}

	private ProgressLogger createLogger(String id) {
		ProgressLogger progressLogger = progressLoggerFactory.newOperation(getClass(), progressGroup);
		progressLogger.setDescription(desc);
		progressLogger.started();
		return progressLogger;
	}

	private void resetLoggers() {
		loggers.values().forEach(ProgressLogger::completed);
		loggers.clear();
	}

	@Override
	public void close() {
		resetLoggers();

		progressGroup.completed();
	}
}
