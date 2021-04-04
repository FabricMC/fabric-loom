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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.processors.MinecraftProcessedProvider;
import net.fabricmc.stitch.util.StitchUtil;

public class GenerateIncrementalSourcesTask extends AbstractLoomTask {
	public final LoomDecompiler decompiler;

	@Inject
	public GenerateIncrementalSourcesTask(LoomDecompiler decompiler) {
		this.decompiler = decompiler;

		getOutputs().upToDateWhen((o) -> false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		GenerateSourcesTask.generateSources(getProject(), getClass(), decompiler, null, false, true);

		if (getExtension().getMappingsProvider().mappedProvider instanceof MinecraftProcessedProvider) {
			JarProcessorManager processorManager = getExtension().getJarProcessorManager();
			Path unprocessedCompiledJar = GenerateSourcesTask.getMappedJarFileWithSuffix(getProject(), null, false).toPath();
			Path compiledJar = GenerateSourcesTask.getMappedJarFileWithSuffix(getProject(), null, true).toPath();
			Path unprocessedSourcesDestination = GenerateSourcesTask.getMappedJarFileWithSuffix(getProject(), "-sources.jar", false).toPath();
			Map<String, byte[]> unprocessedSources = new HashMap<>();
			Set<String> affectedSources = new HashSet<>();

			try (FileSystem system = FileSystems.newFileSystem(URI.create("jar:" + unprocessedSourcesDestination.toUri()), new HashMap<>())) {
				Files.walkFileTree(system.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (file.toString().endsWith(".java")) {
							String sourcePath = file.toString();

							if (sourcePath.length() >= 1 && sourcePath.charAt(0) == '/') {
								sourcePath = sourcePath.substring(1);
							}

							sourcePath = sourcePath.substring(0, sourcePath.length() - 5);
							unprocessedSources.put(sourcePath, Files.readAllBytes(file));

							if (processorManager.doesProcessClass(sourcePath)) {
								affectedSources.add(sourcePath);
							}
						}

						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException exception) {
				exception.printStackTrace();
				unprocessedSources.clear();
			}

			Consumer<Path> linemapConsumer = linemap -> {
				try {
					try (StitchUtil.FileSystemDelegate unprocessedFs = StitchUtil.getJarFileSystem(unprocessedCompiledJar.toFile(), true);
							StitchUtil.FileSystemDelegate processedFs = StitchUtil.getJarFileSystem(compiledJar.toFile(), true)) {
						int i = 0;

						for (Path path : (Iterable<? extends Path>) Files.walk(processedFs.get().getPath("/"))::iterator) {
							if (path.toString().endsWith(".class")) {
								String sourcePath = path.toString();

								if (sourcePath.length() >= 1 && sourcePath.charAt(0) == '/') {
									sourcePath = sourcePath.substring(1);
								}

								sourcePath = sourcePath.substring(0, sourcePath.length() - 6);

								Path unprocessedPath = unprocessedFs.get().getPath(path.toString());

								if (Files.exists(unprocessedPath) && !processorManager.doesProcessClass(sourcePath)) {
									i++;
									Files.copy(unprocessedPath, path, StandardCopyOption.REPLACE_EXISTING);
								}
							}
						}

						getLogger().lifecycle(":copied {} linemap remapped classes from unprocessed jar", i);
					}

					GenerateSourcesTask.remapLinemap(getProject(), getClass(), compiledJar, linemap, true);
				} catch (IOException exception) {
					throw new UncheckedIOException(exception);
				}
			};

			getLogger().lifecycle(":skipping {} unprocessed classes (excluding {} processed classes)", unprocessedSources.size() - affectedSources.size(), affectedSources.size());
			GenerateSourcesTask.generateSources(getProject(), getClass(), decompiler, className -> {
				int i = className.indexOf('$');
				if (i >= 0) className = className.substring(0, i);
				if (affectedSources.contains(className)) return GenerateSourcesTask.SkipState.GENERATE;
				if (unprocessedSources.containsKey(className)) return GenerateSourcesTask.SkipState.SKIP;
				return null;
			}, true, true, linemapConsumer);
		}
	}

	public GenerateIncrementalSourcesTask setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}
}
