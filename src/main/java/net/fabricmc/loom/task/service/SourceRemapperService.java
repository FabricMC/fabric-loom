/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;
import net.fabricmc.lorenztiny.TinyMappingsReader;

public final class SourceRemapperService extends Service<SourceRemapperService.Options> {
	public static ServiceType<Options, SourceRemapperService> TYPE = new ServiceType<>(Options.class, SourceRemapperService.class);

	public interface Options extends Service.Options {
		@Nested
		Property<MappingsService.Options> getMappings();
		@Input
		Property<Integer> getJavaCompileRelease();
		@InputFiles
		ConfigurableFileCollection getClasspath();
	}

	public static Provider<Options> createOptions(RemapSourcesJarTask task) {
		return TYPE.create(task.getProject(), o -> {
			o.getMappings().set(MappingsService.createOptionsWithProjectMappings(
					task.getProject(),
					task.getSourceNamespace(),
					task.getTargetNamespace()
			));
			o.getJavaCompileRelease().set(SourceRemapper.getJavaCompileRelease(task.getProject()));
			o.getClasspath().from(task.getClasspath());
		});
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(SourceRemapperService.class);

	public SourceRemapperService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public void remapSourcesJar(Path source, Path destination) throws IOException {
		if (source.equals(destination)) {
			throw new UnsupportedOperationException("Cannot remap in place");
		}

		Path srcPath = source;
		boolean isSrcTmp = false;

		// Create a temp directory with all of the sources
		if (!Files.isDirectory(source)) {
			isSrcTmp = true;
			srcPath = Files.createTempDirectory("fabric-loom-src");
			ZipUtils.unpackAll(source, srcPath);
		}

		if (!Files.isDirectory(destination) && Files.exists(destination)) {
			Files.delete(destination);
		}

		Mercury mercury = createMercury();

		try (FileSystemUtil.Delegate dstFs = Files.isDirectory(destination) ? null : FileSystemUtil.getJarFileSystem(destination, true)) {
			Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination;

			try {
				mercury.rewrite(srcPath, dstPath);
			} catch (Exception e) {
				LOGGER.warn("Could not remap " + source + " fully!", e);
			}

			SourceRemapper.copyNonJavaFiles(srcPath, dstPath, LOGGER, source);
		} finally {
			if (isSrcTmp) {
				Files.walkFileTree(srcPath, new DeletingFileVisitor());
			}
		}
	}

	private Mercury createMercury() throws IOException {
		var mercury = new Mercury();
		mercury.setGracefulClasspathChecks(true);
		mercury.setSourceCompatibilityFromRelease(getOptions().getJavaCompileRelease().get());

		MappingsService mappingsService = getServiceFactory().get(getOptions().getMappings());
		var tinyMappingsReader = new TinyMappingsReader(mappingsService.getMemoryMappingTree(), mappingsService.getFrom(), mappingsService.getTo()).read();
		mercury.getProcessors().add(MercuryRemapper.create(tinyMappingsReader));

		for (File file : getOptions().getClasspath().getFiles()) {
			if (file.exists()) {
				mercury.getClassPath().add(file.toPath());
			}
		}

		return mercury;
	}
}
