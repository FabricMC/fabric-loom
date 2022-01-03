/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.test.unit

import net.fabricmc.loom.configuration.providers.BundleMetadata
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarSplitter
import spock.lang.Specification

class MinecraftJarSplitterTest extends Specification {
    public static final String CLIENT_JAR_URL = "https://launcher.mojang.com/v1/objects/7e46fb47609401970e2818989fa584fd467cd036/client.jar"
    public static final String SERVER_BUNDLE_JAR_URL = "https://launcher.mojang.com/v1/objects/125e5adf40c659fd3bce3e66e67a16bb49ecc1b9/server.jar"

    public static final File mcJarDir = File.createTempDir()

    def "split jars"() {
        given:
            def clientJar = downloadJarIfNotExists(CLIENT_JAR_URL, "client.jar")
            def serverBundleJar = downloadJarIfNotExists(SERVER_BUNDLE_JAR_URL, "server_bundle.jar")
            def serverJar = new File(mcJarDir, "server.jar")

            def clientOnlyJar = new File(mcJarDir, "client_only.jar")
            def commonJar = new File(mcJarDir, "common.jar")
        when:
            def serverBundleMetadata = BundleMetadata.fromJar(serverBundleJar.toPath())
            serverBundleMetadata.versions().find().unpackEntry(serverBundleJar.toPath(), serverJar.toPath())

            clientOnlyJar.delete()
            commonJar.delete()

            new MinecraftJarSplitter(clientJar.toPath(), serverJar.toPath()).withCloseable {
                it.split(clientOnlyJar.toPath(), commonJar.toPath())
            }
        then:
            serverBundleMetadata.versions().size() == 1
    }

    File downloadJarIfNotExists(String url, String name) {
        File dst = new File(mcJarDir, name)

        if (!dst.exists()) {
            dst.parentFile.mkdirs()
            dst << new URL(url).newInputStream()
        }

        return dst
    }
}
