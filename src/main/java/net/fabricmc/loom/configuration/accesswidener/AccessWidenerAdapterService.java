/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.IsolatedClassLoader;
import net.fabricmc.loom.util.UrlUtil;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

public class AccessWidenerAdapterService implements SharedService {
	private static final String ACCESS_WIDENER_ADAPTER_IMPL_CLASS = "net.fabricmc.loom.configuration.accesswidener.impl.AccessWidenerAdapterImpl";

	public static AccessWidenerAdapter get(Project project) {
		return getService(project, "2.1.0").getAdapter();
	}

	private static AccessWidenerAdapterService getService(Project project, String accessWidenerVersion) {
		final SharedServiceManager sharedServiceManager = SharedServiceManager.get(project);

		// AccessWidenerAdapterService.class.getClassLoader().getResource("/net/fabricmc/loom/accesswidener/AccessWidenerAdapterImpl.class");
		return sharedServiceManager.getOrCreateService("access-widener:" + accessWidenerVersion, () -> {
			final Configuration detachedConfiguration = project.getConfigurations().detachedConfiguration(
					project.getDependencies().create(Constants.Dependencies.ACCESS_WIDENER + accessWidenerVersion)
			);

			final URL[] urls = Stream.concat(
					UrlUtil.streamFileUrls(detachedConfiguration.getFiles()),  // Access widener
					UrlUtil.getClassUrls(
						AccessWidenerAdapterService.class // Loom (Includes the AW adapter impl)
					)
			).toArray(URL[]::new);

			return new AccessWidenerAdapterService(new AdapterClassLoader(urls));
		});
	}

	private final AdapterClassLoader classLoader;
	private AccessWidenerAdapter adapter;

	public AccessWidenerAdapterService(AdapterClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public AccessWidenerAdapter getAdapter() {
		if (adapter == null) {
			adapter = classLoader.loadAdapter();
		}

		return adapter;
	}

	@Override
	public void close() throws IOException {
		classLoader.close();
	}

	private static final class AdapterClassLoader extends IsolatedClassLoader {
		private AdapterClassLoader(URL[] urls) {
			super(urls, List.of(
					AccessWidenerAdapter.class.getName(),
					"org.objectweb.asm",
					"org.slf4j"
			));
		}

		private AccessWidenerAdapter loadAdapter() {
			try {
				Class<?> klass = this.loadClass(ACCESS_WIDENER_ADAPTER_IMPL_CLASS);
				return (AccessWidenerAdapter) klass.getConstructor().newInstance();
			} catch (Exception e) {
				throw new RuntimeException("Failed to load access widener adapter", e);
			}
		}
	}
}
