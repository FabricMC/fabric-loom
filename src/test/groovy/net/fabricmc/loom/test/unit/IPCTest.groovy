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

import net.fabricmc.loom.util.ipc.IPCClient
import net.fabricmc.loom.util.ipc.IPCServer
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.util.function.Consumer

@Timeout(20)
class IPCTest extends Specification {
    def "ipc test"() {
        given:
            def path = Files.createTempFile("loom", "ipc")
            Files.deleteIfExists(path)

            def received = []
            Consumer<String> consumer = { str ->
                println str
                received << str
            }

        when:
            def ipcServer = new IPCServer(path, consumer)

            new IPCClient(path).withCloseable { client ->
               client.accept("Test")
               client.accept("Hello")
            }

            // Allow ipcServer to finish reading, before closing.
            while (received.size() != 2) { }
            ipcServer.close()

        then:
            received.size() == 2
            received[0] == "Test"
            received[1] == "Hello"
    }
}
