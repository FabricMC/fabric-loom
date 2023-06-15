package net.fabricmc.loom.test.integration

import com.microsoft.java.debug.core.DebugUtility
import com.microsoft.java.debug.core.IBreakpoint
import com.microsoft.java.debug.core.IDebugSession
import com.sun.jdi.Bootstrap
import com.sun.jdi.event.BreakpointEvent
import io.reactivex.disposables.Disposable
import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.ZipUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DebugTest extends Specification implements GradleProjectTestTrait {
    static final String MAPPINGS = "1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.1-v2"

    @Unroll
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
        def genSources = gradle.run(task: "genSources")
        genSources.task(":genSources").outcome == SUCCESS

        def lines = getClassSource(gradle, "net/minecraft/server/dedicated/ServerPropertiesLoader.java").lines().toList()
        int l = 1
        for (final def line in lines) {
            println(l++ + ": " + line)
        }


        // Run the task off thread
        def executor = Executors.newSingleThreadExecutor()
        def resultCF = CompletableFuture.supplyAsync({
            gradle.run(task: "runServer")
        }, executor)

        def debugger = new Debugger(openDebugSession())

        debugger.addBreakpoint("net.minecraft.server.dedicated.ServerPropertiesLoader", 16).thenAccept {
            println "Applied ServerPropertiesLoader breakpoint"
        }

        debugger.start()
        // TODO wait for breakpoints
        debugger.close()

        // Block waiting for the gradle build to finish
        def result = resultCF.get()
        executor.shutdown()

        then:
        result.task(":runServer").outcome == SUCCESS
        true
    }

    private static String getClassSource(GradleProject gradle, String classname, String mappings = MAPPINGS) {
        File sourcesJar = gradle.getGeneratedSources(mappings, "serveronly")
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

    class Debugger implements AutoCloseable {
        final IDebugSession debugSession
        final Disposable breakpointEventsSubscription

        final List<BreakpointEvent> breakpointEvents = Collections.synchronizedList([])

        Debugger(IDebugSession debugSession) {
            this.debugSession = debugSession

            breakpointEventsSubscription = debugSession.getEventHub().breakpointEvents().subscribe { debugEvent ->
                breakpointEvents << debugEvent.event
            }
        }

        CompletableFuture<IBreakpoint> addBreakpoint(String className, int lineNumber) {
            def breakpoint = debugSession.createBreakpoint(
                    className,
                    lineNumber,
                    0,
                    null,
                    null
            )
            return breakpoint.install()
        }

        void start() {
            debugSession.start()
        }

        @Override
        void close() throws Exception {
            breakpointEventsSubscription.dispose()
            debugSession.detach()
        }
    }
}
