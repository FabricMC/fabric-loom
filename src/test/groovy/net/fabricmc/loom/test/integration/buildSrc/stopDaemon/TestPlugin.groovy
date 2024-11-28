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

package net.fabricmc.loom.test.integration.buildSrc.stopDaemon

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.cache.FileLockManager
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.file.Chmod
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.invocation.DefaultGradle
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.protocol.Finished
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry
import org.gradle.launcher.daemon.server.DaemonTcpServerConnector
import org.gradle.launcher.daemon.server.DefaultDaemonConnection
import org.gradle.launcher.daemon.server.IncomingConnectionHandler
import org.gradle.launcher.daemon.server.SynchronizedDispatchConnection
import org.gradle.launcher.daemon.server.api.DaemonState
import org.gradle.util.GradleVersion

import net.fabricmc.loom.util.gradle.daemon.DaemonUtils

/**
 * An integration test that runs a dummy gradle daemon TCP server, to test the daemon shutdown mechanism.
 */
class TestPlugin implements Plugin<Project> {
	static ExecutorFactory executorFactory = new DefaultExecutorFactory()

	@Override
	void apply(Project project) {
		final ServiceRegistry services = ((DefaultGradle) project.getGradle()).getServices()
		final Path registryBin = project.getGradle().getGradleUserHomeDir().toPath()
				.resolve("daemon")
				.resolve(GradleVersion.current().getVersion())
				.resolve("registry.bin")

		// Start a dummy daemon process
		def handler = new TestIncomingConnectionHandler()
		def server = new DaemonTcpServerConnector(executorFactory, new InetAddressFactory(), DaemonMessageSerializer.create(null))
		def address = server.start(handler, handler)

		// Write it in the registry
		def registry = new PersistentDaemonRegistry(registryBin.toFile(), services.get(FileLockManager.class), services.get(Chmod.class))
		def daemonInfo = new DaemonInfo(address, createDaemonContext(), "token".bytes, DaemonState.Busy)
		registry.store(daemonInfo)

		// When we get a connection, wait for a stop message and process it by responding with a success message
		def future = handler.daemonConnection.thenAccept { it.waitForAndProcessStop() }

		// Stop the daemon
		def result = DaemonUtils.stopWhenIdle(DaemonUtils.Context.fromProject(project))

		// Wait for the connection to be processed, this should have already happened, as the above call is blocking
		future.join()

		// And clean up
		server.stop()
		registry.remove(address)

		if (!result) {
			throw new IllegalStateException("Failed to stop daemon")
		}
	}

	static DefaultDaemonContext createDaemonContext() {
		return new DefaultDaemonContext(
				UUID.randomUUID().toString(),
				new File("."),
				JavaLanguageVersion.current(),
				"",
				new File("."),
				ProcessHandle.current().pid(),
				0,
				List.of(),
				false,
				NativeServices.NativeServicesMode.NOT_SET,
				DaemonPriority.NORMAL
				)
	}

	class TestIncomingConnectionHandler implements IncomingConnectionHandler, Runnable, AutoCloseable {
		CompletableFuture<TestDaemonConnection> daemonConnection = new CompletableFuture<>()

		@Override
		void handle(SynchronizedDispatchConnection<Message> connection) {
			if (daemonConnection.isDone()) {
				throw new IllegalStateException("Already have a connection?")
			}

			daemonConnection.complete(new TestDaemonConnection(connection, executorFactory))
		}

		@Override
		void run() {
			throw new IllegalStateException("Should not be called")
		}

		@Override
		void close() throws Exception {
			if (daemonConnection.isDone()) {
				daemonConnection.get().stop()
			}
		}
	}

	class TestDaemonConnection extends DefaultDaemonConnection {
		SynchronizedDispatchConnection<Message> dispatchConnection

		TestDaemonConnection(SynchronizedDispatchConnection<Message> connection, ExecutorFactory executorFactory) {
			super(connection, executorFactory)
			this.dispatchConnection = connection
		}

		void waitForAndProcessStop() {
			def response = receive(1, TimeUnit.MINUTES)

			if (!(response instanceof StopWhenIdle)) {
				throw new IllegalStateException("Expected StopWhenIdle, got ${response}")
			}
			println("Received stop message ${response}")

			dispatchConnection.dispatchAndFlush(new Success("Ok"))
			response = receive(1, TimeUnit.MINUTES)

			if (!(response instanceof Finished)) {
				throw new IllegalStateException("Expected Finished, got ${response}")
			}

			println("Received finished message ${response}")
		}
	}
}
