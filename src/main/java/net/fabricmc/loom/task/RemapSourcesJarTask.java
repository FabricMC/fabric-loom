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

import javax.inject.Inject;

import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.task.service.ClientEntriesService;
import net.fabricmc.loom.task.service.SourceRemapperService;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

public abstract class RemapSourcesJarTask extends AbstractRemapJarTask {
	@Nested
	abstract Property<SourceRemapperService.Options> getSourcesRemapperServiceOptions();

	@Inject
	public RemapSourcesJarTask() {
		super();
		getClasspath().from(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		getJarType().set("sources");

		getSourcesRemapperServiceOptions().set(SourceRemapperService.createOptions(this));
	}

	@TaskAction
	public void run() {
		submitWork(RemapSourcesAction.class, params -> {
			if (!params.namespacesMatch()) {
				params.getSourcesRemapperServiceOptions().set(getSourcesRemapperServiceOptions());
			}
		});
	}

	@Override
	protected Provider<? extends ClientEntriesService.Options> getClientOnlyEntriesOptionsProvider(SourceSet clientSourceSet) {
		return ClientEntriesService.Source.createOptions(getProject(), clientSourceSet);
	}

	public interface RemapSourcesParams extends AbstractRemapParams {
		Property<SourceRemapperService.Options> getSourcesRemapperServiceOptions();
	}

	public abstract static class RemapSourcesAction extends AbstractRemapAction<RemapSourcesParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapSourcesAction.class);

		public RemapSourcesAction() {
			super();
		}

		@Override
		public void execute() {
			try {
				if (!getParameters().namespacesMatch()) {
					try (var serviceFactory = new ScopedServiceFactory()) {
						SourceRemapperService sourceRemapperService = serviceFactory.get(getParameters().getSourcesRemapperServiceOptions());
						sourceRemapperService.remapSourcesJar(inputFile, outputFile);
					}
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
