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

import net.fabricmc.loom.util.Pair
import net.fabricmc.loom.util.ZipUtils
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ZipUtilsTest extends Specification {
    def "pack"() {
        given:
            def dir = File.createTempDir()
            def zip = File.createTempFile("loom-zip-test", ".zip").toPath()
            new File(dir, "test.txt").text = "This is a test of packing"

        when:
            ZipUtils.pack(dir.toPath(), zip)

        then:
            Files.exists(zip)
            ZipUtils.contains(zip, "test.txt")
            !ZipUtils.contains(zip, "nope.txt")
            new String( ZipUtils.unpack(zip, "test.txt"), StandardCharsets.UTF_8) == "This is a test of packing"
    }

    def "transform string"() {
        given:
            def dir = File.createTempDir()
            def zip = File.createTempFile("loom-zip-test", ".zip").toPath()
            new File(dir, "test.txt").text = "This is a test of transforming"

        when:
            ZipUtils.pack(dir.toPath(), zip)
            def transformed = ZipUtils.transformString(zip, [
                    new Pair<String, ZipUtils.UnsafeUnaryOperator<String>>("test.txt", new ZipUtils.UnsafeUnaryOperator<String>() {
                        @Override
                        String apply(String arg) throws IOException {
                            return arg.toUpperCase()
                        }
                    })
            ])

        then:
            transformed == 1
            ZipUtils.contains(zip, "test.txt")
            new String( ZipUtils.unpack(zip, "test.txt"), StandardCharsets.UTF_8) == "THIS IS A TEST OF TRANSFORMING"
    }

    def "replace string"() {
        given:
            def dir = File.createTempDir()
            def zip = File.createTempFile("loom-zip-test", ".zip").toPath()
            new File(dir, "test.txt").text = "This has not been replaced"

        when:
            ZipUtils.pack(dir.toPath(), zip)
            ZipUtils.replace(zip, "test.txt", "This has been replaced".bytes)

        then:
            ZipUtils.contains(zip, "test.txt")
            new String(ZipUtils.unpack(zip, "test.txt"), StandardCharsets.UTF_8) == "This has been replaced"
    }

    def "add file"() {
        given:
            def dir = File.createTempDir()
            def zip = File.createTempFile("loom-zip-test", ".zip").toPath()
            new File(dir, "test.txt").text = "This is original"

        when:
            ZipUtils.pack(dir.toPath(), zip)
            ZipUtils.add(zip, "test2.txt", "This has been added".bytes)

        then:
            ZipUtils.contains(zip, "test.txt")
            ZipUtils.contains(zip, "test2.txt")
            new String(ZipUtils.unpack(zip, "test.txt"), StandardCharsets.UTF_8) == "This is original"
            new String(ZipUtils.unpack(zip, "test2.txt"), StandardCharsets.UTF_8) == "This has been added"
    }

    def "unpack all"() {
        given:
            def input = File.createTempDir()
            def output = File.createTempDir()

            def zip = File.createTempFile("loom-zip-test", ".zip").toPath()
            new File(input, "test.txt").text = "This is a test of unpacking all"

            def outputFile = new File(output, "test.txt")

        when:
            ZipUtils.pack(input.toPath(), zip)
            ZipUtils.unpackAll(zip, output.toPath())

        then:
            outputFile.exists()
            outputFile.text == "This is a test of unpacking all"
    }
}
