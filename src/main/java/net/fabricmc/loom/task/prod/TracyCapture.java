/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.prod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.ExceptionUtil;

public abstract class TracyCapture {
	private static final Logger LOGGER = LoggerFactory.getLogger(TracyCapture.class);

	/**
	 * The path to the tracy-capture executable.
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getTracyCapture();

	/**
	 * The maximum number of seconds to wait for tracy-capture to stop on its own before killing it.
	 *
	 * <p>Defaults to 10 seconds.
	 */
	@Input
	public abstract Property<Integer> getMaxShutdownWaitSeconds();

	/**
	 * The path to the output file.
	 */
	@OutputFile
	@Optional
	public abstract RegularFileProperty getOutput();

	@Inject
	public TracyCapture() {
		getMaxShutdownWaitSeconds().convention(10);
	}

	void runWithTracy(IORunnable runnable) throws IOException {
		TracyCaptureRunner tracyCaptureRunner = createRunner();

		boolean success = false;

		try {
			runnable.run();
			success = true;
		} finally {
			try {
				tracyCaptureRunner.close();
			} catch (Exception e) {
				if (success) {
					//noinspection ThrowFromFinallyBlock
					throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to stop tracy capture", e);
				}
			}
		}
	}

	private TracyCaptureRunner createRunner() throws IOException {
		File tracyCapture = getTracyCapture().getAsFile().get();
		File output = getOutput().getAsFile().get();

		ProcessBuilder builder = new ProcessBuilder()
				.command(tracyCapture.getAbsolutePath(), "-a", "127.0.0.1", "-f", "-o", output.getAbsolutePath());
		Process process = builder.start();

		captureLog(process.getInputStream(), LOGGER::info);
		captureLog(process.getErrorStream(), LOGGER::error);

		LOGGER.info("Tracy capture started");

		return new TracyCaptureRunner(process, getMaxShutdownWaitSeconds().get());
	}

	private record TracyCaptureRunner(Process process, int shutdownWait) implements AutoCloseable {
		@Override
		public void close() throws Exception {
			// Wait x seconds for tracy to stop on its own
			// This allows time for tracy to save the profile to disk
			for (int i = 0; i < shutdownWait; i++) {
				if (!process.isAlive()) {
					break;
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}

			// If it's still running, kill it
			if (process.isAlive()) {
				LOGGER.error("Tracy capture did not stop on its own, killing it");
				process.destroy();
				process.waitFor();
			}

			int exitCode = process.exitValue();

			if (exitCode != 0) {
				throw new RuntimeException("Tracy capture failed with exit code " + exitCode);
			}
		}
	}

	private static void captureLog(InputStream inputStream, Consumer<String> lineConsumer) {
		new Thread(() -> {
			try {
				new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(lineConsumer);
			} catch (Exception e) {
				// Don't really care, this will happen when the stream is closed
			}
		}).start();
	}

	@FunctionalInterface
	public interface IORunnable {
		void run() throws IOException;
	}
}
