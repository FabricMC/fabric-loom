/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsResolver
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import org.gradle.api.logging.Logger
import spock.lang.Specification

class MojangMappingsResolverTest extends Specification {
    Logger mockLogger = Mock(Logger)

    def "test"() {
        when:
            def tempDir = File.createTempDir()
            def clientFile = new File(tempDir, "client.txt")
            def serverFile = new File(tempDir, "server.txt")

            def clientDownload = new MinecraftVersionMeta.Download("client.txt", CLIENT_SHA1, 0, CLIENT_URL)
            def serverDownload = new MinecraftVersionMeta.Download("server.txt", SERVER_SHA1, 0, SERVER_URL)
            def resolver = new MojangMappingsResolver(clientDownload, serverDownload, clientFile, serverFile, mockLogger)

            //resolver.visit()
    }

    private static final String CLIENT_URL = "https://launcher.mojang.com/v1/objects/227d16f520848747a59bef6f490ae19dc290a804/client.txt"
    private static final String CLIENT_SHA1 = "227d16f520848747a59bef6f490ae19dc290a804"
    private static final String SERVER_URL = "https://launcher.mojang.com/v1/objects/84d80036e14bc5c7894a4fad9dd9f367d3000334/server.txt"
    private static final String SERVER_SHA1 = "84d80036e14bc5c7894a4fad9dd9f367d3000334"
}
