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

package net.fabricmc.loom.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.task.service.SourceRemapperService;
import net.fabricmc.loom.util.service.BuildSharedServiceManager;
import net.fabricmc.loom.util.service.UnsafeWorkQueueHelper;

public abstract class RemapSourcesJarTask extends AbstractRemapJarTask {
	private final Provider<BuildSharedServiceManager> serviceManagerProvider;

	@Inject
	public RemapSourcesJarTask() {
		super();
		serviceManagerProvider = BuildSharedServiceManager.createForTask(this, getBuildEventsListenerRegistry());

		getClasspath().from(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		getJarType().set("sources");
	}

	@TaskAction
	public void run() {
		submitWork(RemapSourcesAction.class, params -> {
			if (!params.namespacesMatch()) {
				params.getSourcesRemapperServiceUuid().set(UnsafeWorkQueueHelper.create(SourceRemapperService.create(serviceManagerProvider.get().get(), this)));
			}
		});
	}

	@Override
	protected List<String> getClientOnlyEntries(SourceSet clientSourceSet) {
		return clientSourceSet.getAllSource().getFiles().stream()
				.map(relativePath(getRootPaths(clientSourceSet.getAllSource().getSrcDirs())))
				.toList();
	}

	public interface RemapSourcesParams extends AbstractRemapParams {
		Property<String> getSourcesRemapperServiceUuid();
	}

	public abstract static class RemapSourcesAction extends AbstractRemapAction<RemapSourcesParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapSourcesAction.class);

		private final @Nullable SourceRemapperService sourceRemapperService;

		public RemapSourcesAction() {
			super();

			sourceRemapperService = getParameters().getSourcesRemapperServiceUuid().isPresent()
					? UnsafeWorkQueueHelper.get(getParameters().getSourcesRemapperServiceUuid(), SourceRemapperService.class)
					: null;
		}

		@Override
		public void execute() {
			try {
				if (sourceRemapperService != null) {
					sourceRemapperService.remapSourcesJar(inputFile, outputFile);
				} else {
					Files.copy(inputFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
				}

				modifyJarManifest();
				rewriteJar();
			} catch (Exception e) {
				try {
					Files.deleteIfExists(outputFile);
				} catch (IOException ex) {
					LOGGER.error("Failed to delete output file", ex);
				}

				throw new RuntimeException("Failed to remap sources", e);
			}
		}
	}
}
