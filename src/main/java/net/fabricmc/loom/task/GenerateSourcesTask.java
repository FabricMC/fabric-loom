/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.base.MoreObjects;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.ProgressLogger;
import net.fabricmc.stitch.util.StitchUtil;

public class GenerateSourcesTask extends AbstractLoomTask {
	public final LoomDecompiler decompiler;

	private File inputJar;

	@Inject
	public GenerateSourcesTask(LoomDecompiler decompiler) {
		this.decompiler = decompiler;

		getOutputs().upToDateWhen((o) -> false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		generateSources(getProject(), getClass(), decompiler, null, true, false);
	}

	public static void generateSources(Project project, Class<?> taskClass, LoomDecompiler decompiler, Function<String, SkipState> additionalClassFilter, boolean processed, boolean incremental)
			throws IOException {
		generateSources(project, taskClass, decompiler, additionalClassFilter, processed, incremental, linemap -> {
			try {
				Path compiledJar = getMappedJarFileWithSuffix(project, null, processed).toPath();
				remapLinemap(project, taskClass, compiledJar, linemap, processed);
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
		});
	}

	public static void generateSources(Project project, Class<?> taskClass, LoomDecompiler decompiler, Function<String, SkipState> additionalClassFilter, boolean processed, boolean incremental, Consumer<Path> linemapConsumer)
			throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		int threads = Runtime.getRuntime().availableProcessors();
		Path javaDocs = extension.getMappingsProvider().tinyMappings.toPath();
		Collection<Path> libraries = project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).collect(Collectors.toSet());

		Path runtimeJar = getMappedJarFileWithSuffix(project, null, processed).toPath();
		Path sourcesDestination = getMappedJarFileWithSuffix(project, "-sources.jar", processed).toPath();
		Path linemap = getMappedJarFileWithSuffix(project, "-sources.lmap", processed).toPath();
		Map<String, byte[]> remappedClasses = new HashMap<>();

		if (!LoomGradlePlugin.refreshDeps && incremental && Files.exists(sourcesDestination)) {
			try (FileSystem system = FileSystems.newFileSystem(URI.create("jar:" + sourcesDestination.toUri()), new HashMap<>())) {
				Files.walkFileTree(system.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (file.toString().endsWith(".java")) {
							String sourcePath = file.toString();

							if (sourcePath.length() >= 1 && sourcePath.charAt(0) == '/') {
								sourcePath = sourcePath.substring(1);
							}

							remappedClasses.put(sourcePath.substring(0, sourcePath.length() - 5), Files.readAllBytes(file));
						}

						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException exception) {
				exception.printStackTrace();
				remappedClasses.clear();
			}
		}

		Files.deleteIfExists(sourcesDestination);

		if (incremental) {
			project.getLogger().lifecycle(":incremental source generation: skipping {} classes", remappedClasses.size());
		}

		Function<String, SkipState> function = MoreObjects.firstNonNull(additionalClassFilter, s -> null);
		Function<String, SkipState> classFilter = remappedClasses.isEmpty() ? function : s -> {
			SkipState apply = function.apply(s);
			if (apply != null) return apply;
			int i = s.indexOf('$');
			if (i >= 0) s = s.substring(0, i);
			return remappedClasses.containsKey(s) ? SkipState.SKIP : SkipState.GENERATE;
		};
		DecompilationMetadata metadata = new DecompilationMetadata(threads, javaDocs, libraries, classFilter);
		decompiler.decompile(inputJar.toPath(), sourcesDestination, linemap, metadata);

		if (incremental && !remappedClasses.isEmpty()) {
			try (FileSystem system = FileSystems.newFileSystem(URI.create("jar:" + sourcesDestination.toUri()), new HashMap<String, String>() {
				{
					put("create", "true");
				}
			})) {
				for (Map.Entry<String, byte[]> entry : remappedClasses.entrySet()) {
					if (additionalClassFilter != null && SkipState.SKIP != additionalClassFilter.apply(entry.getKey())) continue;
					Path path = system.getPath(entry.getKey() + ".java");
					Path parent = path.getParent();
					if (parent != null) Files.createDirectories(parent);
					Files.write(path, entry.getValue(), StandardOpenOption.CREATE);
				}
			}
		}
	}

	public static void remapLinemap(Project project, Class<?> taskClass, Path compiledJar, Path linemap, boolean processed) throws IOException {
		if (Files.exists(linemap)) {
			Path linemappedJarDestination = getMappedJarFileWithSuffix(project, "-linemapped.jar", processed).toPath();

			// Line map the actually jar used to run the game, not the one used to decompile
			remapLineNumbers(runtimeJar, linemap, linemappedJarDestination);

			Files.copy(linemappedJarDestination, runtimeJar, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(linemappedJarDestination);
		}
	}

	private static void remapLineNumbers(Project project, Class<?> taskClass, Path oldCompiledJar, Path linemap, Path linemappedJarDestination)
			throws IOException {
		project.getLogger().info(":adjusting line numbers");
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemap.toFile());

		ProgressLogger progressLogger = ProgressLogger.getProgressFactory(project, taskClass.getName());
		progressLogger.start("Adjusting line numbers", "linemap");

		try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(oldCompiledJar.toFile(), true);
			StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(linemappedJarDestination.toFile(), true)) {
			remapper.process(progressLogger, inFs.get().getPath("/"), outFs.get().getPath("/"));
		}

		progressLogger.completed();
	}

	public static File getMappedJarFileWithSuffix(Project project, String suffix, boolean processed) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = processed ? mappingsProvider.mappedProvider.getMappedJar() : mappingsProvider.mappedProvider.getUnprocessedMappedJar();
		String path = mappedJar.getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		if (suffix == null) {
			return new File(path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	public enum SkipState {
		SKIP, GENERATE;
	}

	@InputFile
	public File getInputJar() {
		return inputJar;
	}

	public GenerateSourcesTask setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}
}
