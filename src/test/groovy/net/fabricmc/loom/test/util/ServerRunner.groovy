/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.test.util

import groovy.transform.Immutable

import java.util.concurrent.TimeUnit

class ServerRunner {
    static final String LOADER_VERSION = "0.12.5"
    static final Map<String, String> FABRIC_API_URLS = [
            "1.16.5": "https://github.com/FabricMC/fabric/releases/download/0.37.1%2B1.16/fabric-api-0.37.1+1.16.jar",
            "1.17.1": "https://github.com/FabricMC/fabric/releases/download/0.37.1%2B1.17/fabric-api-0.37.1+1.17.jar"
    ]
    static final String FABRIC_LANG_KOTLIN = "https://maven.fabricmc.net/net/fabricmc/fabric-language-kotlin/1.7.3%2Bkotlin.1.6.20/fabric-language-kotlin-1.7.3%2Bkotlin.1.6.20.jar"

    final File serverDir
    final String minecraftVersion

    final List<File> mods = []

    private ServerRunner(File serverDir, String minecraftVersion) {
        this.serverDir = serverDir
        this.minecraftVersion = minecraftVersion

        this.serverDir.mkdirs()
    }

    static ServerRunner create(File testProjectDir, String minecraftVersion) {
        return new ServerRunner(new File(testProjectDir, "server"), minecraftVersion)
    }

    def install() {
        def args = [
                "server",
                "-dir",
                serverDir.absolutePath,
                "-mcversion",
                minecraftVersion,
                "-loader",
                LOADER_VERSION,
                "-downloadMinecraft"
        ]

        //noinspection UnnecessaryQualifiedReference
        net.fabricmc.installer.Main.main(args as String[])

        def eulaFile = new File(serverDir, "eula.txt")
        eulaFile << "eula=true"

        def serverPropsFile = new File(serverDir, "server.properties")
        serverPropsFile << "level-type=FLAT" // Generates the world faster
    }

    ServerRunner withMod(File file) {
        mods << file
        return this
    }

    ServerRunner downloadMod(String url, String filename) {
        File modFile = new File(serverDir, "downloadedMods/" + filename)
        modFile.parentFile.mkdirs()

        println("Downloading " + url)
        modFile.bytes = new URL(url).bytes

        return withMod(modFile)
    }

    ServerRunner withFabricApi() {
        if (!FABRIC_API_URLS[minecraftVersion]) {
            throw new UnsupportedOperationException("No Fabric api url for " + minecraftVersion)
        }

        return downloadMod(FABRIC_API_URLS[minecraftVersion], "fabricapi.jar")
    }

    ServerRunResult run() {
        install()

        // Copy the mods here so we can
        mods.each {
            if (!it.exists()) {
                throw new FileNotFoundException(it.absolutePath)
            }

            File modFile = new File(serverDir, "mods/" + it.name)
            modFile.parentFile.mkdirs()
            modFile.bytes = it.bytes
        }

        String javaExecutablePath = ProcessHandle.current()
                .info()
                .command()
                .orElseThrow()

        var builder = new ProcessBuilder()
        builder.directory(serverDir)
        builder.command(javaExecutablePath, "-Xmx1G", "-jar", "fabric-server-launch.jar", "nogui")

        Process process = builder.start()
        def out = new StringBuffer()
        def isStopping = false

        process.consumeProcessOutput(
                new ForwardingAppendable([System.out, out], {
                    if (!isStopping && out.contains("Done ") && out.contains("For help, type \"help\"")) {
                        isStopping = true

                        Thread.start {
                            println("Stopping server in 5 seconds")
                            sleep(5000)

                            println("Sending stop command")
                            process.outputStream.withCloseable {
                                it.write("stop\n".bytes)
                            }
                        }
                    }
                }),
                new ForwardingAppendable([System.err, out])
        )

        addShutdownHook {
            if (process.alive) {
                process.destroy()
            }
        }

        assert process.waitFor(10, TimeUnit.MINUTES)
        int exitCode = process.exitValue()

        println("Sever closed with exit code: " + exitCode)

        return new ServerRunResult(exitCode, out.toString())
    }

    @Immutable
    class ServerRunResult {
        int exitCode
        String output

        boolean successful() {
            return exitCode == 0 && output.contains("Done ")
        }
    }

    private class ForwardingAppendable implements Appendable {
        final List<Appendable> appendables
        final Closure onAppended

        ForwardingAppendable(List<Appendable> appendables, Closure onAppended = {}) {
            this.appendables = appendables
            this.onAppended = onAppended
        }

        @Override
        Appendable append(CharSequence csq) throws IOException {
            appendables.each {
                it.append(csq)
            }

            onAppended.run()
            return this
        }

        @Override
        Appendable append(CharSequence csq, int start, int end) throws IOException {
            appendables.each {
                it.append(csq, start, end)
            }

            onAppended.run()
            return this
        }

        @Override
        Appendable append(char c) throws IOException {
            appendables.each {
                it.append(c)
            }

            onAppended.run()
            return this
        }
    }
}
