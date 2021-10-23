/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.util.Constants;

public abstract class AbstractRunTask extends JavaExec {
	private final RunConfig config;

	public AbstractRunTask(Function<Project, RunConfig> configProvider) {
		super();
		setGroup(Constants.TaskGroup.FABRIC);
		this.config = configProvider.apply(getProject());

		setClasspath(config.sourceSet.getRuntimeClasspath());
		args(config.programArgs);
	}

	@Override
	public void exec() {
		setWorkingDir(new File(getProject().getRootDir(), config.runDir));

		super.exec();
	}

	@Override
	public void setWorkingDir(File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}

		super.setWorkingDir(dir);
	}

	@Override
	public String getMain() {
		return config.mainClass;
	}

	@Override
	public List<String> getJvmArgs() {
		List<String> superArgs = super.getJvmArgs();
		List<String> args = new ArrayList<>(superArgs != null ? superArgs : Collections.emptyList());
		args.addAll(config.vmArgs);
		return args;
	}
}
