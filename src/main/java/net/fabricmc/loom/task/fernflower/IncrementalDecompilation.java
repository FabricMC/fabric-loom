package net.fabricmc.loom.task.fernflower;

import net.fabricmc.loom.task.ApplyLinemappedJarTask;
import org.apache.tools.ant.util.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class IncrementalDecompilation {

	public static Optional<Path> getClosestCompiledJar(Path closestTo,Path compiledJarsDir) throws IOException {
		Stream<Path> candidateJars = Files.list(compiledJarsDir);

		// Return the newest of available jars
		return candidateJars.filter(p -> !p.getFileName().equals(closestTo.getFileName())).max((pathA, pathB) -> {
			try {
				//TODO: this algorithm can probably be improved
				return Files.getLastModifiedTime(pathA).compareTo(Files.getLastModifiedTime(pathB));
			} catch (IOException e) {
				throw new RuntimeException("Could not get last modified time of compiled jar", e);
			}
		});

	}

	private final Path newCompiledJar;
	private final @Nullable Path oldCompiledJar;
	private final List<String> unchangedClasses;
	private final List<String> changedClasses;
	private final Logger logger;

	public @Nullable Path getOldCompiledJar() {
		return oldCompiledJar;
	}

	public IncrementalDecompilation(Path newCompiledJar, @Nullable Path oldCompiledJar, Logger logger) {
		this.newCompiledJar = newCompiledJar;
		this.oldCompiledJar = oldCompiledJar;
		this.logger = logger;

		unchangedClasses = new ArrayList<>();
		changedClasses = new ArrayList<>();
		diffCompiledJars(unchangedClasses, changedClasses);
	}

	/**
	 * @return A directory that contains the changed classfiles
	 */
	public Path getChangedClassfilesFile() throws IOException {
		logStatus();
		if (unchangedClasses.isEmpty()) return newCompiledJar;

		// copy over the classfiles we want to be decompiled to a normal directory
		Path changedClassfilesDir = Files.createTempDirectory("compiled");

		try (FileSystem compiledJarFs = FileSystems.newFileSystem(newCompiledJar, null)) {
			for (String changedClass : changedClasses) {
				String path = changedClass + ".class";
				Path tempClassfileForFF = Paths.get(changedClassfilesDir.toString(), path);
				Files.createDirectories(tempClassfileForFF.getParent());
				Files.copy(compiledJarFs.getPath(path), tempClassfileForFF);
			}
		}
		return changedClassfilesDir;
	}

	/**
	 * Since we don't decompile the entire jar then the line mapping is not complete,
	 * however we can reuse the old linemapped jar and copy the unchanged parts over to the new partially linemapped jar
	 */
	public void useUnchangedLinemappedClassFiles(Path oldLinemappedJar, Path newLinemappedJar) throws IOException {
		try (FileSystem oldLinemappedFs = FileSystems.newFileSystem(oldLinemappedJar, null);
			 FileSystem newCompiledFs = FileSystems.newFileSystem(newLinemappedJar, null)
		) {
			for (String unchangedClass : unchangedClasses) {
				String path = unchangedClass + ".class";
				Files.copy(oldLinemappedFs.getPath(path), newCompiledFs.getPath(path), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	public void addUnchangedSourceFiles(Path newSourceFile, @NonNull Path oldSourceFile) throws IOException {
		// Copy over unchanged sources
		try (FileSystem oldSourcesFs = FileSystems.newFileSystem(oldSourceFile, null)) {
			try (FileSystem newSourcesFs = FileSystems.newFileSystem(newSourceFile, null)) {
				for (String unchangedClass : unchangedClasses) {
					// Inner classes are included as part of the outer classes in source files
					if (isInnerClass(unchangedClass)) continue;
					String path = unchangedClass + ".java";
					Path targetPath = newSourcesFs.getPath(path);
					Files.createDirectories(targetPath.getParent());
					Files.copy(oldSourcesFs.getPath(path), targetPath);
				}
			}
		}
	}


	private void diffCompiledJars(List<String> unchangedClasses, List<String> changedClasses) {
		if (oldCompiledJar == null) return;
		try (FileSystem newFs = FileSystems.newFileSystem(newCompiledJar, null)) {
			try (FileSystem oldFs = FileSystems.newFileSystem(oldCompiledJar, null)) {
				addClassfiles(newFs, oldFs, unchangedClasses, changedClasses);
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot diff compiled jars", e);
		}
	}


	private void logStatus() {
		if (oldCompiledJar != null) {
			logger.lifecycle(String.format("%s was already decompiled; Reusing %d old classes and redecompiling %d changed classes",
					oldCompiledJar.getFileName(), unchangedClasses.size(), changedClasses.size()
			));
		} else {
			logger.lifecycle("There are no older named minecraft jars that have been decompiled in store; The sources jar will be decompiled from scratch");
		}
	}


	/**
	 * @return A directory that contains the changed classfiles
	 */
	private static void addClassfiles(FileSystem jarFs, @Nullable FileSystem closestJarFs, List<String> unchanged, List<String> changed) throws IOException {
		for (Path rootDir : jarFs.getRootDirectories()) {
			Files.walk(rootDir).forEach(newJarPath -> {
				if (Files.isDirectory(newJarPath) || !newJarPath.toString().endsWith(".class")) return;
				try {
					String pathString = StringUtils.removeSuffix(newJarPath.toString(), ".class");

					if (outerOrInnerClassesChanged(closestJarFs, newJarPath)) {
						// Nothing to reuse from an old jar, decompile it
						changed.add(pathString);
					} else {
						//TODO: filter out inner classes
						unchanged.add(pathString);
					}

				} catch (IOException e) {
					throw new RuntimeException("Could not visit compiled jar file", e);
				}

			});
		}
	}



	private static boolean changed(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
		Path classFileInOldJar = oldJarFs != null ? oldJarFs.getPath(classFile.toString()) : null;

		if (classFileInOldJar == null || !Files.exists(classFileInOldJar)) return true;
		byte[] newClassFile = Files.readAllBytes(classFile);
		byte[] oldClassFile = Files.readAllBytes(classFileInOldJar);
		return !Arrays.equals(newClassFile, oldClassFile);
	}

	/**
	 * In compiled jars, inner classes are separated into separate files, while in source jars, they are merged into one file.
	 * This means that when the inner class has changed we need to make sure the outer class gets decompiled too, and when
	 * and outer class gets changed we need to make sure the inner class gets decompiled too.
	 */
	private static boolean outerOrInnerClassesChanged(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
		String outerClassName = getOuterClassName(classFile);

		AtomicBoolean changed = new AtomicBoolean(false);
		Files.list(classFile.getParent()).forEach(pathInDir -> {
			try {
				if (changed.get()) return;
				if (Files.isDirectory(pathInDir)) return;
				if (getOuterClassName(pathInDir).equals(outerClassName) && changed(oldJarFs, pathInDir)) {
					changed.set(true);
				}
			} catch (IOException e) {
				throw new RuntimeException("Cannot check if classfile was changed", e);
			}
		});
		return changed.get();
	}

	private static String getOuterClassName(Path path) {
		String className = path.getFileName().toString();
		int index = className.indexOf("$");
		String withExtension = index == -1 ? className : className.substring(0, index);
		return withExtension.endsWith(".class") ? withExtension.
				substring(0, withExtension.length() - ".class".length()) : withExtension;
	}

	private static boolean isInnerClass(String path) {
		return path.contains("$");
	}
}
