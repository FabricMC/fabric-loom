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

package net.fabricmc.loom.task.testResult;

import static java.time.Instant.now;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Stack;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.ipc.IPCServer;

@SuppressWarnings("UnstableApiUsage")
public abstract class GameTestReporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(GameTestReporter.class);

	@OutputDirectory
	protected abstract DirectoryProperty getBinaryResultsDirectory();

	@OutputDirectory
	protected abstract DirectoryProperty getHtmlReportDirectory();

	@OutputFile
	protected abstract RegularFileProperty getIPCDomainSocketFile();

	@Inject
	protected abstract TestEventReporterFactory getTestEventReporterFactory();

	@Inject
	protected abstract Project getProject();

	@Inject
	public GameTestReporter(String name) {
		ReportingExtension reporting = getProject().getExtensions().getByType(ReportingExtension.class);
		LoomGradleExtension extension = getProject().getExtensions().getByType(LoomGradleExtension.class);

		getBinaryResultsDirectory().convention(getProject().getLayout().getBuildDirectory().dir("test-results/" + name));
		getHtmlReportDirectory().convention(reporting.getBaseDirectory().dir("tests/" + name));
		getIPCDomainSocketFile().set(new File(extension.getFiles().getProjectBuildCache(), "ipc/%s.sock".formatted(name)));
	}

	@Internal
	public String getJvmArgumentForTestProcess() {
		return "-Dfabric.gameTest.reporting.ipcPath=" + getIPCDomainSocketFile().get().getAsFile().getAbsolutePath();
	}

	public Runner run() {
		return new Runner(getTestEventReporterFactory(),
				getBinaryResultsDirectory().get(),
				getHtmlReportDirectory().get(),
				getIPCDomainSocketFile().get().getAsFile().toPath()
		);
	}

	public static class Runner implements Closeable {
		private final GroupTestEventReporter root;
		private final MessageConsumer messageConsumer;
		private final IPCServer ipcServer;

		private Runner(TestEventReporterFactory factory,
						Directory binaryResultsDirectory,
						Directory htmlReportDirectory,
						Path ipcDomainSocketFile) {
			root = factory.createTestEventReporter(
					"Tests",
					binaryResultsDirectory,
					htmlReportDirectory
			);
			messageConsumer = new MessageConsumer(root);
			ipcServer = new IPCServer(ipcDomainSocketFile, new GameTestIPCMessageDeserializer(messageConsumer));
		}

		@Override
		public void close() {
			// Close in the reverse order of creation

			try {
				ipcServer.close();
			} catch (InterruptedException e) {
				throw new RuntimeException("Failed to close IPC server", e);
			}

			messageConsumer.close();

			root.succeeded(now());
			root.close();
		}
	}

	public static class MessageConsumer implements Closeable, Consumer<GameTestIPCMessage> {
		private final GroupTestEventReporter root;
		private final Stack<GroupTestEventReporter> groups = new Stack<>();

		private @Nullable TestEventReporter runningTest = null;;
		private boolean testFailedInGroup = false;

		public MessageConsumer(GroupTestEventReporter root) {
			this.root = root;
		}

		@Override
		public void accept(GameTestIPCMessage message) {
			if (message instanceof GameTestIPCMessage.PushGroup push) {
				GroupTestEventReporter group = root.reportTestGroup(push.name());
				group.started(now());
				groups.push(group);
				testFailedInGroup = false;
			} else if (message instanceof GameTestIPCMessage.PopGroup) {
				if (groups.isEmpty()) {
					throw new IllegalStateException("No group to pop");
				}

				GroupTestEventReporter group = groups.pop();

				if (testFailedInGroup) {
					group.failed(now());
				} else {
					group.succeeded(now());
				}

				group.close();
			} else if (message instanceof GameTestIPCMessage.StartTest test) {
				if (runningTest != null) {
					throw new IllegalStateException("Test already started");
				}

				runningTest = groups.peek().reportTest(test.name(), test.name());
				runningTest.started(now());
			} else if (message instanceof GameTestIPCMessage.CompleteTest complete) {
				if (runningTest == null) {
					throw new IllegalStateException("No test to complete");
				}

				if (complete.result() == GameTestIPCMessage.TestResult.FAILED) {
					runningTest.failed(now());
					testFailedInGroup = true;
				} else {
					runningTest.succeeded(now());
				}

				runningTest.close();
				runningTest = null;
			} else {
				// TODO replace with switch expression when we get 21
				throw new IllegalArgumentException("Unhandled message type: " + message.getClass().getName());
			}
		}

		@Override
		public void close() {
			if (runningTest != null) {
				runningTest.failed(now());
				runningTest.close();
			}

			if (!groups.isEmpty()) {
				throw new IllegalStateException("Not all groups were closed");
			}
		}
	}
}
