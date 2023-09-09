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

import io.javalin.http.HttpStatus

import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadException

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
			it.status(HttpStatus.NOT_FOUND)
		}

		when:
		def result = Download.create("$PATH/stringNotFound")
				.maxRetries(3) // Ensure we still error as expected when retrying
				.downloadString()

		then:
		def e = thrown DownloadException
		e.statusCode == 404
	}

	def "String: Server error"() {
		setup:
		server.get("/stringNotFound") {
			it.status(HttpStatus.INTERNAL_SERVER_ERROR)
		}

		when:
		def result = Download.create("$PATH/stringNotFound")
				.maxRetries(3) // Ensure we still error as expected when retrying
				.downloadString()

		then:
		def e = thrown DownloadException
		e.statusCode == 500
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

	def "String: Retries"() {
		setup:
		int requests = 0
		server.get("/retryString") {
			requests ++

			if (requests < 3) {
				it.status(HttpStatus.INTERNAL_SERVER_ERROR)
				return
			}

			it.result("Hello World " + requests)
		}

		when:
		def result = Download.create("$PATH/retryString")
				.maxRetries(3)
				.downloadString()

		then:
		result == "Hello World 3"
	}

	def "String: Retries 404"() {
		setup:
		int requests = 0
		server.get("/retryString") {
			requests ++
			it.status(HttpStatus.NOT_FOUND)
		}

		when:
		def result = Download.create("$PATH/retryString")
				.maxRetries(3)
				.downloadString()

		then:
		def e = thrown DownloadException
		e.statusCode == 404
		requests == 1
	}

	def "String: File cache"() {
		setup:
		server.get("/downloadString2") {
			it.result("Hello World!")
		}

		when:
		def output = new File(File.createTempDir(), "file.txt").toPath()
		def result = Download.create("$PATH/downloadString2").downloadString(output)

		then:
		result == "Hello World!"
	}

	def "String: Insecure protocol"() {
		when:
		def result = Download.create("http://fabricmc.net").downloadString()
		then:
		thrown IllegalArgumentException
	}
}
