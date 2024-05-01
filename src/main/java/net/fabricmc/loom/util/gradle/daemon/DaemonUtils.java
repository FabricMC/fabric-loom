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

package net.fabricmc.loom.util.gradle.daemon;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.gradle.api.Project;
import org.gradle.cache.FileLockManager;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.serialize.Serializers;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.StopDispatcher;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This uses a vast amount of Gradle internal APIs, however this is only used when the JVM is in an unrecoverable state.
 * The alternative is to kill the JVM, using System.exit, which is not ideal and leaves scary messages in the log.
 */
public final class DaemonUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(DaemonUtils.class);

	private DaemonUtils() {
	}

	/**
	 * Request the Gradle daemon to stop when it becomes idle.
	 */
	public static void tryStopGradleDaemon(Project project) {
		try {
			stopWhenIdle(project);
		} catch (Throwable t) {
			LOGGER.error("Failed to request the Gradle demon to stop", t);
		}
	}

	@VisibleForTesting
	public static boolean stopWhenIdle(Project project) {
		DaemonInfo daemonInfo = findCurrentDaemon(project);

		if (daemonInfo == null) {
			return false;
		}

		RemoteConnection<Message> connection = null;

		try {
			// Gradle communicates with the daemon using a TCP connection, and a custom binary protocol.
			// We connect to the daemon using the daemon's address, and then send a StopWhenIdle message.
			connection = new TcpOutgoingConnector().connect(daemonInfo.getAddress()).create(Serializers.stateful(DaemonMessageSerializer.create(null)));
			DaemonClientConnection daemonClientConnection = new DaemonClientConnection(connection, daemonInfo, null);
			new StopDispatcher().dispatch(daemonClientConnection, new StopWhenIdle(UUID.randomUUID(), daemonInfo.getToken()));
		} finally {
			if (connection != null) {
				connection.stop();
			}
		}

		LOGGER.warn("Requested Gradle daemon to stop on exit.");
		return true;
	}

	@Nullable
	private static DaemonInfo findCurrentDaemon(Project project) {
		// Gradle maintains a list of running daemons in a registry.bin file.
		final Path registryBin = project.getGradle().getGradleUserHomeDir().toPath().resolve("daemon").resolve(GradleVersion.current().getVersion()).resolve("registry.bin");
		project.getLogger().lifecycle("Looking for daemon in: " + registryBin);

		// We can use a PersistentDaemonRegistry to read this
		final ServiceRegistry services = ((DefaultGradle) project.getGradle()).getServices();
		final DaemonRegistry registry = new PersistentDaemonRegistry(registryBin.toFile(), services.get(FileLockManager.class), services.get(Chmod.class));

		final long pid = ProcessHandle.current().pid();
		final List<DaemonInfo> runningDaemons = registry.getAll();

		LOGGER.info("Found {} running Gradle daemons in registry: {}", runningDaemons.size(), registryBin);

		for (DaemonInfo daemonInfo : runningDaemons) {
			if (daemonInfo.getPid() == pid) {
				return daemonInfo;
			}
		}

		LOGGER.warn("Could not find current process in daemon registry: {}", registryBin);
		return null;
	}
}
