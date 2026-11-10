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

package net.fabricmc.loom.util.download;

import org.gradle.api.Project;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/** Shares successful hash-verified downloads for the lifetime of one Gradle build. */
public abstract class DownloadService implements BuildService<BuildServiceParameters.None> {
	private final DownloadCache cache = new DownloadCache();

	public static DownloadService get(Project project) {
		BuildService<?> service = project.getGradle().getSharedServices()
				.registerIfAbsent("loomDownloadService", DownloadService.class, spec -> { })
				.get();

		if (service instanceof DownloadService downloadService) {
			return downloadService;
		}

		// Projects with different plugin classpaths may load incompatible service classes.
		String serviceName = "loomDownloadService:" + System.identityHashCode(DownloadService.class);
		return project.getGradle().getSharedServices()
				.registerIfAbsent(serviceName, DownloadService.class, spec -> {
					project.getLogger().warn("Loom is loaded by multiple classloaders; Minecraft download verification caches will not be shared between them, which may reduce performance.");
				})
				.get();
	}

	public DownloadCache getCache() {
		return cache;
	}
}
