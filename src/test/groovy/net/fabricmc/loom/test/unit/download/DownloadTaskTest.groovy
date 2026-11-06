/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

import java.nio.file.Path
import java.time.Duration

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.TempDir

import net.fabricmc.loom.task.DownloadTask
import net.fabricmc.loom.util.download.DownloadException

class DownloadTaskTest extends DownloadTest {
	@TempDir
	Path tempDir

	DownloadTask.DownloadWorkParameters parameters
	DownloadTask.DownloadAction action

	def setup() {
		def project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
		parameters = project.objects.newInstance(DownloadTask.DownloadWorkParameters)
		parameters.url.set("$PATH/simpleFile")
		parameters.outputFile.set(tempDir.resolve("out.txt").toFile())
		parameters.isOffline.set(false)
		action = new DownloadTask.DownloadAction() {
					@Override
					DownloadTask.DownloadWorkParameters getParameters() {
						return DownloadTaskTest.this.parameters
					}
				}
		server.get("/simpleFile") {
			it.result("Hello World")
		}
	}

	def "downloads with optional properties"() {
		given:
		if (sha1 != null) {
			parameters.sha1.set(sha1)
		}
		if (maxAge != null) {
			parameters.maxAge.set(maxAge)
		}

		when:
		action.execute()

		then:
		parameters.outputFile.get().asFile.text == "Hello World"

		where:
		sha1 | maxAge
		null | null
		"0a4d55a8d778e5022fab701977c5d840bbc486d0" | null
		null | Duration.ofDays(1)
	}

	def "rejects an incorrect sha1"() {
		given:
		parameters.sha1.set("0000000000000000000000000000000000000000")

		when:
		action.execute()

		then:
		def exception = thrown(RuntimeException)
		exception.message.contains("Failed to download file")
		exception.cause instanceof DownloadException
	}
}
