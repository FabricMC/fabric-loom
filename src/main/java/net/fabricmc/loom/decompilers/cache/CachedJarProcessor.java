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

package net.fabricmc.loom.decompilers.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.util.FileSystemUtil;

public record CachedJarProcessor(CachedFileStore<CachedData> fileStore, String baseHash) {
	private static final Logger LOGGER = LoggerFactory.getLogger(CachedJarProcessor.class);

	public WorkRequest prepareJob(Path inputJar) throws IOException {
		boolean isIncomplete = false;
		boolean hasSomeExisting = false;

		Path incompleteJar = Files.createTempFile("loom-cache-incomplete", ".jar");
		Path existingJar = Files.createTempFile("loom-cache-existing", ".jar");

		// We must delete the empty files, so they can be created as a zip
		Files.delete(incompleteJar);
		Files.delete(existingJar);

		// Sources name -> hash
		Map<String, String> outputNameMap = new HashMap<>();
		Map<String, ClassLineNumbers.Entry> lineNumbersMap = new HashMap<>();

		int hits = 0;
		int misses = 0;

		try (FileSystemUtil.Delegate inputFs = FileSystemUtil.getJarFileSystem(inputJar, false);
				FileSystemUtil.Delegate incompleteFs = FileSystemUtil.getJarFileSystem(incompleteJar, true);
				FileSystemUtil.Delegate existingFs = FileSystemUtil.getJarFileSystem(existingJar, true)) {
			final List<ClassEntry> inputClasses = JarWalker.findClasses(inputFs);

			for (ClassEntry entry : inputClasses) {
				String outputFileName = entry.sourcesFileName();
				String fullHash = baseHash + "/" + entry.hash(inputFs.getRoot());

				final CachedData entryData = fileStore.getEntry(fullHash);

				if (entryData == null) {
					// Cached entry was not found, so copy the input to the incomplete jar to be processed
					entry.copyTo(inputFs.getRoot(), incompleteFs.getRoot());
					isIncomplete = true;
					outputNameMap.put(outputFileName, fullHash);

					LOGGER.debug("Cached entry ({}) not found, going to process {}", fullHash, outputFileName);
					misses++;
				} else {
					final Path outputPath = existingFs.getPath(outputFileName);
					Files.createDirectories(outputPath.getParent());
					Files.writeString(outputPath, entryData.sources());
					lineNumbersMap.put(entryData.className(), entryData.lineNumbers());
					hasSomeExisting = true;

					LOGGER.debug("Cached entry ({}) found: {}", fullHash, outputFileName);
					hits++;
				}
			}
		}

		// A jar file that will be created by the work action, containing the newly processed items.
		Path outputJar = Files.createTempFile("loom-cache-output", ".jar");
		Files.delete(outputJar);

		final ClassLineNumbers lineNumbers = lineNumbersMap.isEmpty() ? null : new ClassLineNumbers(Collections.unmodifiableMap(lineNumbersMap));
		final var stats = new CacheStats(hits, misses);

		if (isIncomplete && !hasSomeExisting) {
			// The cache contained nothing of use, fully process the input jar
			Files.delete(incompleteJar);
			Files.delete(existingJar);

			LOGGER.info("No cached entries found, going to process the whole jar");
			return new FullWorkJob(inputJar, outputJar, outputNameMap)
					.asRequest(stats, lineNumbers);
		} else if (isIncomplete) {
			// The cache did not contain everything so we have some work to do
			LOGGER.info("Some cached entries found, using partial work job");
			return new PartialWorkJob(incompleteJar, existingJar, outputJar, outputNameMap)
					.asRequest(stats, lineNumbers);
		} else {
			// The cached contained everything we need, so the existing jar is the output
			LOGGER.info("All cached entries found, using completed work job");
			Files.delete(incompleteJar);
			return new CompletedWorkJob(existingJar)
					.asRequest(stats, lineNumbers);
		}
	}

	public void completeJob(Path output, WorkJob workJob, ClassLineNumbers lineNumbers) throws IOException {
		if (workJob instanceof CompletedWorkJob completedWorkJob) {
			// Fully complete, nothing new to cache
			Files.move(completedWorkJob.completed(), output);
			return;
		}

		// Work has been done, we need to cache the newly processed items
		if (workJob instanceof WorkToDoJob workToDoJob) {
			// Sources name -> hash
			Map<String, String> outputNameMap = workToDoJob.outputNameMap();

			try (FileSystemUtil.Delegate outputFs = FileSystemUtil.getJarFileSystem(workToDoJob.output(), false);
					Stream<Path> walk = Files.walk(outputFs.getRoot())) {
				Iterator<Path> iterator = walk.iterator();

				while (iterator.hasNext()) {
					final Path fsPath = iterator.next();

					if (fsPath.startsWith("/META-INF/")) {
						continue;
					}

					if (!Files.isRegularFile(fsPath)) {
						continue;
					}

					final String hash = outputNameMap.get(fsPath.toString().substring(outputFs.getRoot().toString().length()));

					if (hash == null) {
						throw new IllegalStateException("Unexpected output: " + fsPath);
					}

					// Trim the leading / and the .java extension
					final String className = fsPath.toString().substring(1, fsPath.toString().length() - ".java".length());
					final String sources = Files.readString(fsPath);

					ClassLineNumbers.Entry lineMapEntry = null;

					if (lineNumbers != null) {
						lineMapEntry = lineNumbers.lineMap().get(className);
					}

					final var cachedData = new CachedData(className, sources, lineMapEntry);
					fileStore.putEntry(hash, cachedData);

					LOGGER.debug("Saving processed entry ({}) to cache: {}", hash, fsPath);
				}
			}
		} else {
			throw new IllegalStateException();
		}

		if (workJob instanceof PartialWorkJob partialWorkJob) {
			// Copy all the existing items to the output jar
			try (FileSystemUtil.Delegate outputFs = FileSystemUtil.getJarFileSystem(partialWorkJob.output(), false);
					FileSystemUtil.Delegate existingFs = FileSystemUtil.getJarFileSystem(partialWorkJob.existing(), false);
					Stream<Path> walk = Files.walk(existingFs.getRoot())) {
				Iterator<Path> iterator = walk.iterator();

				while (iterator.hasNext()) {
					Path existingPath = iterator.next();

					if (!Files.isRegularFile(existingPath)) {
						continue;
					}

					final Path outputPath = outputFs.getRoot().resolve(existingPath.toString());

					LOGGER.debug("Copying existing entry to output: {}", existingPath);
					Files.createDirectories(outputPath.getParent());
					Files.copy(existingPath, outputPath);
				}
			}

			Files.delete(partialWorkJob.existing());
			Files.move(partialWorkJob.output(), output);
		} else if (workJob instanceof FullWorkJob fullWorkJob) {
			// Nothing to merge, just use the output jar
			Files.move(fullWorkJob.output, output);
		} else {
			throw new IllegalStateException();
		}
	}

	public record WorkRequest(WorkJob job, CacheStats stats, @Nullable ClassLineNumbers lineNumbers) {
	}

	public record CacheStats(int hits, int misses) {
	}

	public sealed interface WorkJob permits CompletedWorkJob, WorkToDoJob {
		default WorkRequest asRequest(CacheStats stats, @Nullable ClassLineNumbers lineNumbers) {
			return new WorkRequest(this, stats, lineNumbers);
		}
	}

	public sealed interface WorkToDoJob extends WorkJob permits PartialWorkJob, FullWorkJob {
		/**
		 * A path to jar file containing all the classes to be processed.
		 */
		Path incomplete();

		/**
		 * @return A jar file to be written to during processing
		 */
		Path output();

		/**
		 * @return A map of sources name to hash
		 */
		Map<String, String> outputNameMap();
	}

	/**
	 * No work to be done, all restored from cache.
	 *
	 * @param completed
	 */
	public record CompletedWorkJob(Path completed) implements WorkJob {
	}

	/**
	 * Some work needs to be done.
	 *
	 * @param incomplete A path to jar file containing all the classes to be processed
	 * @param existing A path pointing to a jar containing existing classes that have previously been processed
	 * @param output A path to a temporary jar where work output should be written to
	 * @param outputNameMap A map of sources name to hash
	 */
	public record PartialWorkJob(Path incomplete, Path existing, Path output, Map<String, String> outputNameMap) implements WorkToDoJob {
	}

	/**
	 * The full jar must be processed.
	 *
	 * @param incomplete A path to jar file containing all the classes to be processed
	 * @param output A path to a temporary jar where work output should be written to
	 * @param outputNameMap A map of sources name to hash
	 */
	public record FullWorkJob(Path incomplete, Path output, Map<String, String> outputNameMap) implements WorkToDoJob {
	}
}
