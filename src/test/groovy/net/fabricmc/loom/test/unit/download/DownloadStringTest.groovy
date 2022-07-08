/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.unit.download

import net.fabricmc.loom.util.download.Download

class DownloadStringTest extends DownloadTest {
	def "String: Download"() {
		setup:
			server.get("/downloadString") {
				it.result("Hello World!")
			}

		when:
			def result = Download.create("$PATH/downloadString").downloadString()

		then:
			result == "Hello World!"
	}

	def "String: Not found"() {
		setup:
			server.get("/stringNotFound") {
				it.status(404)
			}

		when:
			def result = Download.create("$PATH/stringNotFound").downloadString()

		then:
			thrown RuntimeException
	}

	def "String: Redirect"() {
		setup:
			server.get("/redirectString2") {
				it.result("Hello World!")
			}
			server.get("/redirectString") {
				it.redirect("$PATH/redirectString2")
			}

		when:
			def result = Download.create("$PATH/redirectString").downloadString()

		then:
			result == "Hello World!"
	}
}
