/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.task.service;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public abstract class ClientEntriesService<O extends ClientEntriesService.Options> extends Service<O> {
	public interface Options extends Service.Options {
	}

	public ClientEntriesService(O options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public abstract List<String> getClientOnlyEntries();

	public static class Source extends ClientEntriesService<Source.Options> {
		public static final ServiceType<Source.Options, Source> TYPE = new ServiceType<>(Source.Options.class, Source.class);

		public interface Options extends ClientEntriesService.Options {
			@InputFiles
			ConfigurableFileCollection getAllSourceFiles();
			@InputFiles
			ConfigurableFileCollection getSourceDirectories();
		}

		public static Provider<Options> createOptions(Project project, SourceSet sourceSet) {
			return TYPE.create(project, o -> {
				o.getAllSourceFiles().from(sourceSet.getAllSource().getAsFileTree());
				o.getSourceDirectories().from(sourceSet.getAllSource().getSourceDirectories());
			});
		}

		public Source(Source.Options options, ServiceFactory serviceFactory) {
			super(options, serviceFactory);
		}

		@Override
		public List<String> getClientOnlyEntries() {
			return getOptions().getAllSourceFiles().getFiles().stream()
					.map(relativePath(getRootPaths(getOptions().getSourceDirectories().getFiles())))
					.toList();
		}
	}

	public static class Classes extends ClientEntriesService<Classes.Options> {
		public static final ServiceType<Classes.Options, Classes> TYPE = new ServiceType<>(Classes.Options.class, Classes.class);

		public interface Options extends ClientEntriesService.Options {
			@InputFiles
			ConfigurableFileCollection getAllOutputDirs();
		}

		public static Provider<Classes.Options> createOptions(Project project, SourceSet sourceSet) {
			return TYPE.create(project, o -> {
				o.getAllOutputDirs().from(sourceSet.getOutput().getClassesDirs());
				o.getAllOutputDirs().from(sourceSet.getOutput().getResourcesDir());
			});
		}

		public Classes(Options options, ServiceFactory serviceFactory) {
			super(options, serviceFactory);
		}

		@Override
		public List<String> getClientOnlyEntries() {
			final Set<File> outputFiles = getOptions().getAllOutputDirs().getAsFileTree().getFiles();
			final List<String> rootPaths = getRootPaths(getOptions().getAllOutputDirs().getFiles());
			return outputFiles.stream()
					.map(relativePath(rootPaths))
					.toList();
		}
	}

	static List<String> getRootPaths(Set<File> files) {
		return files.stream()
				.map(root -> {
					String rootPath = root.getAbsolutePath().replace("\\", "/");

					if (rootPath.charAt(rootPath.length() - 1) != '/') {
						rootPath += '/';
					}

					return rootPath;
				}).toList();
	}

	static Function<File, String> relativePath(List<String> rootPaths) {
		return file -> {
			String s = file.getAbsolutePath().replace("\\", "/");

			for (String rootPath : rootPaths) {
				if (s.startsWith(rootPath)) {
					s = s.substring(rootPath.length());
				}
			}

			return s;
		};
	}
}
