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

package net.fabricmc.loom.util.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BuildSharedServiceManager implements BuildService<BuildServiceParameters.None> {
	private static final Logger LOGGER = LoggerFactory.getLogger(BuildSharedServiceManager.class);
	private static final String NAME = "loom:sharedServiceManager";

	private SharedServiceManager sharedServiceManager = new BuildSharedServiceManagerImpl();
	private final AtomicInteger refCount = new AtomicInteger(0);

	public static Provider<BuildSharedServiceManager> createForTask(Task task, BuildEventsListenerRegistry buildEventsListenerRegistry) {
		Provider<BuildSharedServiceManager> provider = task.getProject().getGradle().getSharedServices().registerIfAbsent(NAME, BuildSharedServiceManager.class, spec -> {
		});
		task.usesService(provider);

		final BuildSharedServiceManager serviceManager = provider.get();
		buildEventsListenerRegistry.onTaskCompletion(registerTaskCompletion(task, serviceManager::onFinish));
		int count = serviceManager.refCount.incrementAndGet();
		LOGGER.debug("Creating shared service manager provider for task: {} count: {}", task.getName(), count);

		return provider;
	}

	public BuildSharedServiceManager() {
		LOGGER.debug("New BuildSharedServiceManager instance");
	}

	public SharedServiceManager get() {
		LOGGER.debug("Shared build service get");
		return Objects.requireNonNull(sharedServiceManager);
	}

	private void onFinish() {
		int count = refCount.decrementAndGet();

		LOGGER.debug("Build service finish. count: {}", count);

		if (count == 0) {
			sharedServiceManager.onFinish();
			sharedServiceManager = null;
		} else if (count < 0) {
			throw new IllegalStateException();
		}
	}

	private static Provider<OperationCompletionListener> registerTaskCompletion(Task task, Runnable runnable) {
		return task.getProject().provider(() -> event -> {
			if (event.getDescriptor() instanceof TaskOperationDescriptor taskDescriptor) {
				if (taskDescriptor.getTaskPath().equals(task.getPath())) {
					runnable.run();
				}
			}
		});
	}

	private static final class BuildSharedServiceManagerImpl extends SharedServiceManager {
	}
}
