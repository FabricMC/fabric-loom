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

package net.fabricmc.loom.test.unit.layeredmappings

import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta

class MojangMappingLayerTest extends LayeredMappingsSpecification {
    def "Read mojang mappings" () {
        setup:
            mockMappingsProvider.intermediaryTinyFile() >> extractFileFromZip(downloadFile(INTERMEDIARY_URL, "intermediary.jar"), "mappings/mappings.tiny")
            mockMinecraftProvider.getVersionInfo() >> versionMeta
        when:
            def mappings = getLayeredMappings(
                    new IntermediaryMappingsSpec(),
                    new MojangMappingsSpec()
            )
            def tiny = getTiny(mappings)
        then:
            mappings.srcNamespace == "named"
            mappings.dstNamespaces == ["intermediary", "official"]
            mappings.classes.size() == 6113
            mappings.classes[0].srcName.hashCode() == 1869546970 // MojMap name, just check the hash
            mappings.classes[0].getDstName(0) == "net/minecraft/class_2354"
            mappings.classes[0].methods[0].args.size() == 0 // No Args
    }

    private final String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/1.17/intermediary-1.17-v2.jar"
    private final Map<String, MinecraftVersionMeta.Download> downloads = [
        client_mappings:new MinecraftVersionMeta.Download(null, "227d16f520848747a59bef6f490ae19dc290a804", 6431705, "https://launcher.mojang.com/v1/objects/227d16f520848747a59bef6f490ae19dc290a804/client.txt"),
        server_mappings:new MinecraftVersionMeta.Download(null, "84d80036e14bc5c7894a4fad9dd9f367d3000334", 4948536, "https://launcher.mojang.com/v1/objects/84d80036e14bc5c7894a4fad9dd9f367d3000334/server.txt")
    ]
    private final MinecraftVersionMeta versionMeta = new MinecraftVersionMeta(null, null, null, 0, downloads, null, null, null, null, 0, null, null, null)
}
