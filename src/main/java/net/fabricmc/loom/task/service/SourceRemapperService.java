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

package net.fabricmc.loom.task.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.lorenztiny.TinyMappingsReader;

public final class SourceRemapperService implements SharedService {
	public static synchronized SourceRemapperService create(RemapSourcesJarTask task) {
		final Project project = task.getProject();
		final String to = task.getTargetNamespace().get();
		final String from = task.getSourceNamespace().get();
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final SharedServiceManager sharedServiceManager = SharedServiceManager.get(project);
		final String id = extension.getMappingsProvider().getBuildServiceName("sourceremapper", from, to);

		return sharedServiceManager.getOrCreateService(id, () ->
				new SourceRemapperService(MappingsService.createDefault(project, from, to), task.getClasspath()
			));
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(SourceRemapperService.class);

	private final MappingsService mappingsService;
	private final ConfigurableFileCollection classpath;

	private final Supplier<Mercury> mercury = Suppliers.memoize(this::createMercury);

	private SourceRemapperService(MappingsService mappingsService, ConfigurableFileCollection classpath) {
		this.mappingsService = mappingsService;
		this.classpath = classpath;
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

		try (FileSystemUtil.Delegate dstFs = Files.isDirectory(destination) ? null : FileSystemUtil.getJarFileSystem(destination, true)) {
			Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination;

			doRemap(srcPath, dstPath, source);
			SourceRemapper.copyNonJavaFiles(srcPath, dstPath, LOGGER, source);
		} finally {
			if (isSrcTmp) {
				Files.walkFileTree(srcPath, new DeletingFileVisitor());
			}
		}
	}

	private synchronized void doRemap(Path srcPath, Path dstPath, Path source) {
		try {
			mercury.get().rewrite(srcPath, dstPath);
		} catch (Exception e) {
			LOGGER.warn("Could not remap " + source + " fully!", e);
		}
	}

	private MappingSet getMappings() throws IOException {
		return new TinyMappingsReader(mappingsService.getMemoryMappingTree(), mappingsService.getFromNamespace(), mappingsService.getToNamespace()).read();
	}

	private Mercury createMercury() {
		var mercury = new Mercury();
		mercury.setGracefulClasspathChecks(true);

		try {
			mercury.getProcessors().add(MercuryRemapper.create(getMappings()));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read mercury mappings", e);
		}

		for (File file : classpath.getFiles()) {
			if (file.exists()) {
				mercury.getClassPath().add(file.toPath());
			}
		}

		return mercury;
	}
}
