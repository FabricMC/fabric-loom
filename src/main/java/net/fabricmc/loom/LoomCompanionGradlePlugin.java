/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.LoomConfigurations;
import net.fabricmc.loom.task.launch.ExportClasspathTask;
import net.fabricmc.loom.util.Constants;

public class LoomCompanionGradlePlugin implements Plugin<Project> {
	public static final String NAME = "net.fabricmc.fabric-loom-companion";

	@Override
	public void apply(Project project) {
		var exportClassPathTask = project.getTasks().register(Constants.Task.EXPORT_CLASSPATH, ExportClasspathTask.class);
		project.getConfigurations().register(Constants.Configurations.EXPORTED_CLASSPATH, LoomConfigurations.Role.CONSUMABLE::apply);
		project.artifacts(artifactHandler -> artifactHandler.add(Constants.Configurations.EXPORTED_CLASSPATH, exportClassPathTask));
	}
}
