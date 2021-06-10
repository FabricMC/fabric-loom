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
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentMappingsSpec
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta

class ParchmentMappingLayerTest extends LayeredMappingsSpecification {
    def "Read parchment mappings" () {
        setup:
            mockMappingsProvider.intermediaryTinyFile() >> extractFileFromZip(downloadFile(INTERMEDIARY_URL, "intermediary.jar"), "mappings/mappings.tiny")
            mockMinecraftProvider.getVersionInfo() >> versionMeta
        when:
            withMavenFile(PARCHMENT_NOTATION, downloadFile(PARCHMENT_URL, "parchment.zip"))
            def mappings = getLayeredMappings(
                    new IntermediaryMappingsSpec(),
                    new MojangMappingsSpec(),
                    new ParchmentMappingsSpec(PARCHMENT_NOTATION, false)
            )
            def tiny = getTiny(mappings)
        then:
            mappings.srcNamespace == "named"
            mappings.dstNamespaces == ["intermediary", "official"]
            mappings.classes.size() == 5747
            mappings.classes[0].srcName.hashCode() == -1112444138 // MojMap name, just check the hash
            mappings.classes[0].getDstName(0) == "net/minecraft/class_2573"
            mappings.classes[0].methods[0].args[0].srcName == "pStack"
    }

    def "Read parchment mappings remove prefix" () {
        setup:
            mockMappingsProvider.intermediaryTinyFile() >> extractFileFromZip(downloadFile(INTERMEDIARY_URL, "intermediary.jar"), "mappings/mappings.tiny")
            mockMinecraftProvider.getVersionInfo() >> versionMeta
        when:
            withMavenFile(PARCHMENT_NOTATION, downloadFile(PARCHMENT_URL, "parchment.zip"))
            def mappings = getLayeredMappings(
                    new IntermediaryMappingsSpec(),
                    new MojangMappingsSpec(),
                    new ParchmentMappingsSpec(PARCHMENT_NOTATION, true)
            )
            def tiny = getTiny(mappings)
        then:
            mappings.srcNamespace == "named"
            mappings.dstNamespaces == ["intermediary", "official"]
            mappings.classes.size() == 5747
            mappings.classes[0].srcName.hashCode() == -1112444138 // MojMap name, just check the hash
            mappings.classes[0].getDstName(0) == "net/minecraft/class_2573"
            mappings.classes[0].methods[0].args[0].srcName == "stack"
    }

    private final String PARCHMENT_NOTATION = "org.parchmentmc.data:parchment-1.16.5:20210608-SNAPSHOT@zip"
    private final String PARCHMENT_URL = "https://ldtteam.jfrog.io/artifactory/parchmentmc-snapshots/org/parchmentmc/data/parchment-1.16.5/20210608-SNAPSHOT/parchment-1.16.5-20210608-SNAPSHOT.zip"
    private final String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.5/intermediary-1.16.5-v2.jar"
    private final Map<String, MinecraftVersionMeta.Download> downloads = [
        client_mappings:new MinecraftVersionMeta.Download(null, "e3dfb0001e1079a1af72ee21517330edf52e6192", 5746047, "https://launcher.mojang.com/v1/objects/e3dfb0001e1079a1af72ee21517330edf52e6192/client.txt"),
        server_mappings:new MinecraftVersionMeta.Download(null, "81d5c793695d8cde63afddb40dde88e3a88132ac", 4400926, "https://launcher.mojang.com/v1/objects/81d5c793695d8cde63afddb40dde88e3a88132ac/server.txt")
    ]
    private final MinecraftVersionMeta versionMeta = new MinecraftVersionMeta(null, null, null, 0, downloads, null, null, null, null, 0, null, null, null)
}
