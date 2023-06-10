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

package net.fabricmc.loom.util.gradle;

import org.gradle.api.Project;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * Add the following snippet to task to prevent tasks running asynchronously with any other task with the same build service.
 *
 * <pre>{@code
 * @ServiceReference(SyncTaskBuildService.NAME)
 * abstract Property<SyncTaskBuildService> getSyncTask();
 * }</pre>
 */
public abstract class SyncTaskBuildService implements BuildService<SyncTaskBuildService.Params> {
	public static final String NAME = "loomSyncTask";

	public static void register(Project project) {
		project.getGradle().getSharedServices().registerIfAbsent(
					NAME,
					SyncTaskBuildService.class,
					spec -> spec.getMaxParallelUsages().set(1)
		);
	}

	public interface Params extends BuildServiceParameters {
	}
}
