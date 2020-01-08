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

package net.fabricmc.loom.task.fernflower;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import org.apache.tools.ant.util.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import net.fabricmc.loom.task.AbstractDecompileTask;
import net.fabricmc.loom.task.ApplyLinemappedJarTask;
import net.fabricmc.loom.task.ForkingJavaExecTask;
import net.fabricmc.loom.util.ConsumingOutputStream;
import net.fabricmc.loom.util.OperatingSystem;

/**
 * Created by covers1624 on 9/02/19.
 */
public class FernFlowerTask extends AbstractDecompileTask implements ForkingJavaExecTask {
	private boolean noFork = false;
	private boolean enableIncrementalDecompilation = true;
	private int numThreads = Runtime.getRuntime().availableProcessors();

	@TaskAction
	public void doTask() throws Throwable {
		if (!OperatingSystem.is64Bit()) {
			throw new UnsupportedOperationException("FernFlowerTask requires a 64bit JVM to run due to the memory requirements");
		}

		Path input = getInput().toPath();

		IncrementalDecompilation incrementalDecompilation = getIncrementalDecompilation(getNonLinemappedJarOf(input));

		Path toDecompile = incrementalDecompilation == null ? input : incrementalDecompilation.getChangedClassfilesFile();

		Map<String, Object> options = new HashMap<>();
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
		options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
		options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
		getLogging().captureStandardOutput(LogLevel.LIFECYCLE);

		List<String> args = new ArrayList<>();

		options.forEach((k, v) -> args.add(format("-{0}={1}", k, v)));
		args.add(toDecompile.toAbsolutePath().toString());
		args.add("-o=" + getOutput().getAbsolutePath());

		if (getLineMapFile() != null) {
			args.add("-l=" + getLineMapFile().getAbsolutePath());
		}

		args.add("-t=" + getNumThreads());
		args.add("-m=" + getExtension().getMappingsProvider().tinyMappings.getAbsolutePath());

		//TODO, Decompiler breaks on jemalloc, J9 module-info.class?
		getLibraries().forEach(f -> args.add("-e=" + f.getAbsolutePath()));

		ServiceRegistry registry = ((ProjectInternal) getProject()).getServices();
		ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
		ProgressLogger progressGroup = factory.newOperation(getClass()).setDescription("Decompile");
		Supplier<ProgressLogger> loggerFactory = () -> {
			ProgressLogger pl = factory.newOperation(getClass(), progressGroup);
			pl.setDescription("decompile worker");
			pl.started();
			return pl;
		};
		Stack<ProgressLogger> freeLoggers = new Stack<>();
		Map<String, ProgressLogger> inUseLoggers = new HashMap<>();

		progressGroup.started();
		ExecResult result = javaexec(spec -> {
			spec.setMain(ForkedFFExecutor.class.getName());
			spec.jvmArgs("-Xms200m", "-Xmx3G");
			spec.setArgs(args);
			spec.setErrorOutput(System.err);
			spec.setStandardOutput(new ConsumingOutputStream(line -> {
				if (line.startsWith("Listening for transport") || !line.contains("::")) {
					System.out.println(line);
					return;
				}

				int sepIdx = line.indexOf("::");
				String id = line.substring(0, sepIdx).trim();
				String data = line.substring(sepIdx + 2).trim();

				ProgressLogger logger = inUseLoggers.get(id);

				String[] segs = data.split(" ");

				if (segs[0].equals("waiting")) {
					if (logger != null) {
						logger.progress("Idle..");
						inUseLoggers.remove(id);
						freeLoggers.push(logger);
					}
				} else {
					if (logger == null) {
						if (!freeLoggers.isEmpty()) {
							logger = freeLoggers.pop();
						} else {
							logger = loggerFactory.get();
						}

						inUseLoggers.put(id, logger);
					}

					logger.progress(data);
				}
			}));
		});
		inUseLoggers.values().forEach(ProgressLogger::completed);
		freeLoggers.forEach(ProgressLogger::completed);
		progressGroup.completed();

		result.rethrowFailure();
		result.assertNormalExitValue();

		if (incrementalDecompilation != null) addUnchangedSourceFiles(incrementalDecompilation);
		passIncrementalDecompilation(incrementalDecompilation);
	}

	@Nullable
	private IncrementalDecompilation getIncrementalDecompilation(Path input) throws IOException {
		Path closestCompiledJar = IncrementalDecompilation.getClosestCompiledJar(
						input,
						ApplyLinemappedJarTask.jarsBeforeLinemapping(getExtension())
		).orElse(null);

		Path closestSourceJar = getSourceFileOf(closestCompiledJar);

		if (closestSourceJar == null) {
			getLogger().warn("Could not find sources jar of previously decompiled jar: " + closestCompiledJar + ", jar will be decompiled from scratch");
		}

		return enableIncrementalDecompilation && closestCompiledJar != null && closestSourceJar != null
						? new IncrementalDecompilation(input, closestCompiledJar, getLogger())
						: null;
	}

	private void passIncrementalDecompilation(@Nullable IncrementalDecompilation ic) {
		ApplyLinemappedJarTask applyLinemappedJarTask = (ApplyLinemappedJarTask) getProject().getTasks().getByName("genSources");
		applyLinemappedJarTask.setIncrementalDecompilation(ic);
	}

	private void addUnchangedSourceFiles(IncrementalDecompilation incrementalDecompilation) throws IOException {
		Path closestCompiledJar = incrementalDecompilation.getOldCompiledJar();
		if (closestCompiledJar == null) return;
		Path closestSourceJar = getSourceFileOf(closestCompiledJar);

		if (closestSourceJar == null) {
			getLogger().warn("Could not find sources jar of previously decompiled jar: " + closestCompiledJar);
			return;
		}

		incrementalDecompilation.addUnchangedSourceFiles(getOutput().toPath(), closestSourceJar);
	}

	private Path getNonLinemappedJarOf(Path compiledJar) throws IOException {
		Path savedNonLinemappedPath = ApplyLinemappedJarTask.jarsBeforeLinemapping(getExtension()).resolve(compiledJar.getFileName());
		// Before genSources runs on a jar, the non-linemapped jar is the one stored in the top-level loom cache and is used in the IDE.
		// Afterwards, the one in the top-level gets linemapped and the non-linemapped one is stored in ApplyLinemappedJarTask.jarsBeforeLinemapping.
		return Files.exists(savedNonLinemappedPath) ? savedNonLinemappedPath : compiledJar;
	}

	private @Nullable Path getSourceFileOf(@Nullable Path compiledJar) {
		if (compiledJar == null) return null;
		String compiledName = compiledJar.getFileName().toString();
		String sourcesName = StringUtils.removeSuffix(compiledName, ".jar") + "-sources.jar";

		Path sourceFile = getExtension().getUserCache().toPath().resolve(sourcesName);

		return Files.exists(sourceFile) ? sourceFile : null;
	}

	@Input
	public int getNumThreads() {
		return numThreads;
	}

	@Input
	public boolean isNoFork() {
		return noFork;
	}

	@Input
	public boolean isEnableIncrementalDecompilation() {
		return enableIncrementalDecompilation;
	}

	public void setEnableIncrementalDecompilation(boolean enableIncrementalDecompilation) {
		this.enableIncrementalDecompilation = enableIncrementalDecompilation;
	}

	public void setNoFork(boolean noFork) {
		this.noFork = noFork;
	}

	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
}
