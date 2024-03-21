/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.io.Closeable;
import java.io.IOException;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public class ProgressGroup implements Closeable {
	private final String name;
	private final ProgressLoggerFactory progressLoggerFactory;

	private ProgressLogger progressGroup;

	public ProgressGroup(Project project, String name) {
		this.name = name;
		this.progressLoggerFactory = ((ProjectInternal) project).getServices().get(ProgressLoggerFactory.class);
	}

	public ProgressGroup(String name, ProgressLoggerFactory progressLoggerFactory) {
		this.name = name;
		this.progressLoggerFactory = progressLoggerFactory;
	}

	private void start() {
		this.progressGroup = this.progressLoggerFactory.newOperation(name).setDescription(name);
		this.progressGroup.started();
	}

	public ProgressLogger createProgressLogger(String name) {
		if (progressGroup == null) {
			start();
		}

		ProgressLogger progressLogger = this.progressLoggerFactory.newOperation(getClass(), progressGroup);
		progressLogger.setDescription(name);
		progressLogger.start(name, null);
		return progressLogger;
	}

	@Override
	public void close() throws IOException {
		if (this.progressGroup != null) {
			this.progressGroup.completed();
			this.progressGroup = null;
		}
	}
}
