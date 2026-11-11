/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.util.download.DownloadException;
import net.fabricmc.loom.util.download.DownloadExecutor;
import net.fabricmc.loom.util.download.DownloadResult;
import net.fabricmc.loom.util.service.GlobalService;
import net.fabricmc.loom.util.service.LoomBuildService;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public final class DownloadCacheService extends Service<DownloadCacheService.Options> implements GlobalService {
	public static final ServiceType<Options, DownloadCacheService> TYPE = new ServiceType<>(Options.class, DownloadCacheService.class);

	private @Nullable DownloadResult cachedResult;

	public interface Options extends Service.Options {
		@OutputFile
		RegularFileProperty getOutputFile();

		@Input
		Property<String> getUrl();

		@Input
		@Optional
		Property<String> getExpectedHash();

		@Input
		Property<Boolean> getRefresh();

		@Input
		Property<Boolean> getOffline();
	}

	public DownloadCacheService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public synchronized DownloadResult download(DownloadExecutor.DownloadRunner runner) throws DownloadException {
		Path path = getOptions().getOutputFile().get().getAsFile().toPath();
		String hash = getOptions().getExpectedHash().getOrNull();
		boolean offline = getOptions().getOffline().get();

		if (hash != null && !offline && cachedResult != null
				&& Files.isRegularFile(path)
				&& !Files.exists(path.resolveSibling(path.getFileName() + ".lock"))) {
			return cachedResult;
		}

		cachedResult = null;
		DownloadResult result = runner.run();

		if (hash != null && !offline) {
			cachedResult = result;
		}

		return result;
	}

	public static final class Lookup {
		private final Project project;
		private final ServiceFactory serviceFactory;

		private Lookup(Project project, ServiceFactory serviceFactory) {
			this.project = project;
			this.serviceFactory = serviceFactory;
		}

		public static Lookup forProject(Project project) {
			return new Lookup(project, LoomBuildService.get(project));
		}

		public DownloadCacheService get(Path output, String url, @Nullable String expectedHash, boolean refresh, boolean offline) {
			return serviceFactory.get(TYPE.create(project, options -> {
				options.getOutputFile().fileValue(output.toAbsolutePath().normalize().toFile());
				options.getUrl().set(url);

				if (expectedHash != null) {
					options.getExpectedHash().set(expectedHash);
				}

				options.getRefresh().set(refresh);
				options.getOffline().set(offline);
			}));
		}
	}
}
