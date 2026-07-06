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

package net.fabricmc.loom.test.unit.nativeplatform

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import javax.swing.JFrame
import javax.swing.SwingUtilities

import org.junit.jupiter.api.Assumptions
import spock.lang.Requires
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import net.fabricmc.loom.util.nativeplatform.LoomNativePlatform

@Requires({
	os.windows
})
class LoomNativePlatformTest extends Specification {
	def "is supported on windows"() {
		expect:
		LoomNativePlatform.isSupported()
	}

	def "finds current process holding file lock"() {
		given:
		Path path = Files.createTempFile("loom-native-platform", ".txt")

		when:
		def processes = []
		Files.newOutputStream(path).withCloseable {
			processes = LoomNativePlatform.getProcessesWithLockOn(path)
		}

		then:
		processes.size() == 1
		processes.first().pid() == ProcessHandle.current().pid()

		cleanup:
		Files.deleteIfExists(path)
	}

	def "unlocked file has no locking processes"() {
		given:
		Path path = Files.createTempFile("loom-native-platform", ".txt")

		expect:
		LoomNativePlatform.getProcessesWithLockOn(path).empty

		cleanup:
		Files.deleteIfExists(path)
	}

	def "missing file has no locking processes"() {
		given:
		Path path = Files.createTempDirectory("loom-native-platform").resolve("missing.txt")

		expect:
		LoomNativePlatform.getProcessesWithLockOn(path).empty

		cleanup:
		Files.deleteIfExists(path.parent)
	}

	def "pid zero has no window titles"() {
		expect:
		LoomNativePlatform.getWindowTitlesForPid(0).empty
	}

	def "finds window title for process"() {
		given:
		String title = "loom-native-platform-${UUID.randomUUID()}"
		def process = startWindowTitleProcess(title)

		when:
		waitForReady(process)

		then:
		new PollingConditions(timeout: 10).eventually {
			assert LoomNativePlatform.getWindowTitlesForPid(process.pid()).contains(title)
		}

		cleanup:
		process?.destroyForcibly()
	}

	private static Process startWindowTitleProcess(String title) {
		def java = Path.of(System.getProperty("java.home"), "bin", "java.exe")
		return new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"), WindowTitleProcess.name, title)
				.redirectErrorStream(true)
				.start()
	}

	private static void waitForReady(Process process) {
		def reader = process.inputReader()
		def output = new StringBuilder()
		long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

		while (System.nanoTime() < timeout) {
			if (reader.ready()) {
				def line = reader.readLine()

				if (line == null) {
					break
				}

				output.append(line).append('\n')

				if (line == "READY") {
					return
				}

				if (line.startsWith("UNAVAILABLE:")) {
					Assumptions.assumeTrue(false, line)
				}
			} else if (!process.isAlive()) {
				break
			} else {
				Thread.sleep(50)
			}
		}

		throw new AssertionError("Window test process did not become ready. Exit: ${process.isAlive() ? 'still running' : process.exitValue()}, output:\n${output}")
	}
}

class WindowTitleProcess {
	static void main(String[] args) {
		try {
			SwingUtilities.invokeAndWait {
				def frame = new JFrame(args[0])
				frame.setSize(320, 180)
				frame.setLocationRelativeTo(null)
				frame.setVisible(true)
			}

			println("READY")
			System.out.flush()

			while (System.in.read() != -1) {
				Thread.sleep(100)
			}
		} catch (Throwable e) {
			println("UNAVAILABLE: ${e.class.name}: ${e.message}")
			System.out.flush()
			System.exit(2)
		}
	}
}
