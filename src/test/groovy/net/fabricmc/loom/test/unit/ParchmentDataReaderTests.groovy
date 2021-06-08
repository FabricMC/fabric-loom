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

import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentDataReader
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentFileResolver
import net.fabricmc.mappingio.tree.MemoryMappingTree
import spock.lang.Shared
import spock.lang.Specification

class ParchmentDataReaderTests extends Specification {
    static final String TEST_DATA_URL = "https://ldtteam.jfrog.io/artifactory/parchmentmc-snapshots/org/parchmentmc/data/parchment-1.16.5/20210608-SNAPSHOT/parchment-1.16.5-20210608-SNAPSHOT.zip"

    @Shared
    File tempDir = File.createTempDir()
    @Shared
    File parchmentFile = new File(tempDir, "parchment.zip")

    ParchmentFileResolver mockParchmentFileResolver = Mock(ParchmentFileResolver)

    @SuppressWarnings('unused')
    def setupSpec() {
        parchmentFile << new URL(TEST_DATA_URL).newInputStream()
    }

    def "testParse"() {
        setup:
            mockParchmentFileResolver.resolve() >> parchmentFile
        when:
            def data = new ParchmentDataReader(mockParchmentFileResolver).parchmentData
        then:
            data.classes().size() == 3571
    }

    def "testVisit"() {
        setup:
            mockParchmentFileResolver.resolve() >> parchmentFile
        when:
            def data = new ParchmentDataReader(mockParchmentFileResolver).parchmentData
            def tree = new MemoryMappingTree()
            data.visit(tree, "mojmap", "named")

            def mainClass = tree.getClass("net/minecraft/client/main/Main")
        then:
            tree.srcNamespace == "mojmap"
            tree.dstNamespaces[0] == "named"
            tree.classes.size() == 3571
            mainClass != null
            mainClass.methods.size() == 3
            mainClass.methods[1].getComment() != null
    }
}
