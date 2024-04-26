/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.integration

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.microsoft.java.debug.core.DebugUtility
import com.microsoft.java.debug.core.IDebugSession
import com.sun.jdi.Bootstrap
import com.sun.jdi.event.BreakpointEvent
import groovy.transform.CompileStatic
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.functions.Function
import spock.lang.Specification
import spock.lang.Timeout

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar
import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 30, unit = TimeUnit.MINUTES)
class DebugLineNumbersTest extends Specification implements GradleProjectTestTrait {
	static final String MAPPINGS = "1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.1-v2"
	static final Map<String, Integer> BREAKPOINTS = [
		"net.minecraft.server.dedicated.ServerPropertiesLoader": 16,
		"net.minecraft.server.dedicated.MinecraftDedicatedServer": 107,
		"net.minecraft.registry.RegistryOps": 67
	]

	def "Debug test"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << '''
                loom {
                    // Just test with the server, no need to also decompile the client
                    serverOnlyMinecraftJar()
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.20.1"
                    mappings "net.fabricmc:yarn:1.20.1+build.1:v2"
                    modImplementation 'net.fabricmc:fabric-loader:0.14.21'
                }

                runServer {
                    debugOptions {
                       enabled = true
                       port = 8050
                       host = "*"
                       server = true
                       suspend = true
                   }
               }
            '''
		when:
		// First generate sources
		def genSources = gradle.run(task: "genSources", args: ["--info"])
		genSources.task(":genSources").outcome == SUCCESS

		// Print out the source of the file
		def lines = getClassSource(gradle, "net/minecraft/server/dedicated/ServerPropertiesLoader.java").lines().toList()
		int l = 1
		for (final def line in lines) {
			//println(l++ + ": " + line)
		}

		// I agree
		def runDir = new File(gradle.projectDir, "run")
		runDir.mkdirs()
		new File(runDir, "eula.txt") << "eula=true"
		new File(runDir, "server.properties") << ""

		// Run the gradle task off thread
		def executor = Executors.newSingleThreadExecutor()
		def resultCF = CompletableFuture.supplyAsync({
			gradle.run(task: "runServer")
		}, executor)

		Map<String, CompletableFuture<BreakpointEvent>> futures
		def debugger = new Debugger(openDebugSession())

		try {
			futures = BREAKPOINTS.collectEntries { className, line ->
				[(className): debugger.addBreakpoint(className, line)]
			}

			// Start running the game, the process has been suspended until this point.
			debugger.start()

			// Wait for all of the breakpoints
			futures.values().forEach {
				def result = it.get()
				println("Breakpoint triggered: ${result.location()}")
			}

			println("All breakpoints triggered")
		} finally {
			// Close the debugger and target process
			debugger.close()
		}

		def result = resultCF.get()
		executor.shutdown()

		then:
		result.task(":runServer").outcome == SUCCESS

		BREAKPOINTS.forEach { className, line ->
			futures[className].get().location().lineNumber() == line
		}
	}

	private static String getClassSource(GradleProject gradle, String classname, String mappings = MAPPINGS) {
		File sourcesJar = gradle.getGeneratedSources(mappings, MinecraftJar.Type.SERVER.toString())
		return new String(ZipUtils.unpack(sourcesJar.toPath(), classname), StandardCharsets.UTF_8)
	}

	private static IDebugSession openDebugSession() {
		int timeout = 5
		int maxTimeout = 120 / timeout

		for (i in 0..maxTimeout) {
			try {
				return DebugUtility.attach(
						Bootstrap.virtualMachineManager(),
						"127.0.0.1",
						8050,
						timeout
						)
			} catch (ConnectException e) {
				Thread.sleep(timeout * 1000)
				if (i == maxTimeout) {
					throw e
				}
			}
		}

		throw new IllegalStateException()
	}

	@CompileStatic // Makes RxJava somewhat usable in Groovy
	class Debugger implements AutoCloseable {
		final IDebugSession debugSession

		Debugger(IDebugSession debugSession) {
			this.debugSession = debugSession

			debugSession.eventHub.events().subscribe({ }) {
				// Manually bail out, as it seems this can be called after close()
				it.printStackTrace()
				System.exit(-1)
			}
		}

		CompletableFuture<BreakpointEvent> addBreakpoint(String className, int lineNumber) {
			def breakpoint = debugSession.createBreakpoint(
					className,
					lineNumber,
					0,
					null,
					null
					)

			// Wait for the breakpoint to be installed
			return breakpoint.install().thenCompose {
				// Then compose with the first result
				return breakpointEvents()
						.filter { event ->
							event.location().sourcePath().replaceAll("[\\\\/]", ".") == className + ".java" &&
									event.location().lineNumber() == lineNumber
						}
						.firstElement()
						.to(toCompletionStage())
			}
		}

		private static <T> Function<Maybe<T>, CompletionStage<T>> toCompletionStage() {
			return { Maybe<T> m ->
				CompletableFuture<T> cf = new CompletableFuture<>()
				m.subscribe(cf.&complete, cf.&completeExceptionally) { cf.complete(null) }
				return cf
			}
		}

		Observable<BreakpointEvent> breakpointEvents() {
			return debugSession.getEventHub().breakpointEvents()
					.map {
						it.shouldResume = true
						it.event as BreakpointEvent
					}
		}

		void start() {
			debugSession.start()
		}

		@Override
		void close() throws Exception {
			debugSession.terminate()
			debugSession.eventHub.close()
		}
	}
}
