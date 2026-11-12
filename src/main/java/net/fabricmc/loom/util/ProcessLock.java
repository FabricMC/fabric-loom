/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public final class ProcessLock {
	private final Path file;
	private final String description;
	private final Project project;

	public ProcessLock(Path file, String description, Project project) {
		this.file = file;
		this.description = description;
		this.project = project;
	}

	@Override
	public String toString() {
		return description;
	}

	public enum LockResult {
		// acquired immediately or after waiting for another process to release
		ACQUIRED_CLEAN,
		// already owned by current pid
		ACQUIRED_ALREADY_OWNED,
		// acquired due to current owner not existing
		ACQUIRED_PREVIOUS_OWNER_MISSING,
		// acquired due to previous owner disowning the lock
		ACQUIRED_PREVIOUS_OWNER_DISOWNED
	}

	public LockResult acquire() {
		return acquire(getDefaultTimeout());
	}

	public LockResult acquire(Duration timeout) {
		try {
			return acquireWaiting(timeout);
		} catch (final IOException e) {
			throw new RuntimeException("Exception acquiring lock " + this, e);
		}
	}

	private LockResult acquireWaiting(Duration timeout) throws IOException {
		while (true) {
			try {
				return tryAcquire(timeout);
			} catch (FileAlreadyExistsException ignored) {
				// Another process created the lock after we checked it, try again.
			}
		}
	}

	@SuppressWarnings("BusyWait")
	private LockResult tryAcquire(Duration timeout) throws IOException {
		final long timeoutMs = timeout.toMillis();
		final Logger logger = Logging.getLogger("loom_acquireProcessLockWaiting");
		final long currentPid = ProcessHandle.current().pid();
		boolean abrupt = false;
		boolean disowned = false;

		if (Files.exists(file)) {
			long lockingProcessId = -1;

			try {
				String lockValue = Files.readString(file);

				if ("disowned".equals(lockValue)) {
					disowned = true;
				} else {
					lockingProcessId = Long.parseLong(lockValue);
					logger.lifecycle("\"{}\" is currently held by pid '{}'.", this, lockingProcessId);
				}
			} catch (final Exception ignored) {
				// ignored
			}

			if (lockingProcessId == currentPid) {
				return LockResult.ACQUIRED_ALREADY_OWNED;
			}

			Optional<ProcessHandle> handle = ProcessHandle.of(lockingProcessId);

			if (disowned) {
				logger.lifecycle("Previous process has disowned the lock due to abrupt termination.");
				Files.deleteIfExists(file);
			} else if (lockingProcessId >= 0 && handle.isEmpty()) {
				logger.lifecycle("Locking process does not exist, assuming abrupt termination and deleting lock file.");
				Files.deleteIfExists(file);
				abrupt = true;
			} else {
				if (handle.isPresent()) {
					ProcessUtil processUtil = ProcessUtil.create(project);
					logger.lifecycle(processUtil.printWithParents(handle.get()));
				}

				logger.lifecycle("Waiting for lock to be released...");
				long sleptMs = 0;

				while (Files.exists(file)) {
					try {
						if ("disowned".equals(Files.readString(file))) {
							logger.lifecycle("Previous process has disowned the lock due to abrupt termination.");
							Files.deleteIfExists(file);
							disowned = true;
							break;
						}
					} catch (NoSuchFileException e) {
						break;
					}

					try {
						Thread.sleep(100);
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					sleptMs += 100;

					if (sleptMs >= 1000 * 60 && sleptMs % (1000 * 60) == 0L) {
						logger.lifecycle(
								"""
										Have been waiting on "{}" held by pid '{}' for {} minute(s).
										If this persists for an unreasonable length of time, kill this process, run './gradlew --stop' and then try again.""",
								this, lockingProcessId, sleptMs / 1000 / 60
						);
					}

					if (sleptMs >= timeoutMs) {
						throw new GradleException("Have been waiting on lock file '%s' for %s ms. Giving up as timeout is %s ms."
								.formatted(this, sleptMs, timeoutMs));
					}
				}
			}
		}

		if (!Files.exists(file.getParent())) {
			Files.createDirectories(file.getParent());
		}

		Files.writeString(file, String.valueOf(currentPid), StandardOpenOption.CREATE_NEW);

		if (disowned) {
			return LockResult.ACQUIRED_PREVIOUS_OWNER_DISOWNED;
		} else if (abrupt) {
			return LockResult.ACQUIRED_PREVIOUS_OWNER_MISSING;
		}

		return LockResult.ACQUIRED_CLEAN;
	}

	private static Duration getDefaultTimeout() {
		if (System.getenv("CI") != null) {
			// Set a small timeout on CI, as it's unlikely going to unlock.
			return Duration.ofMinutes(1);
		}

		return Duration.ofHours(1);
	}

	// Allow the next run to rebuild without waiting for this process to exit.
	public void disown() {
		final Path lock = file;

		try {
			Files.writeString(lock, "disowned");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void release() {
		final Path lock = file;

		if (!Files.exists(lock)) {
			return;
		}

		try {
			Files.delete(lock);
		} catch (IOException e1) {
			try {
				// If we failed to delete the lock file, moving it before trying to delete it may help.
				final Path del = lock.resolveSibling(lock.getFileName() + ".del");
				Files.move(lock, del);
				Files.delete(del);
			} catch (IOException e2) {
				var exception = new UncheckedIOException("Failed to release getProject() configuration lock", e2);
				exception.addSuppressed(e1);
				throw exception;
			}
		}
	}
}
