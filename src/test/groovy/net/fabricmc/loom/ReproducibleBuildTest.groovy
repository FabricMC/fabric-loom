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

package net.fabricmc.loom

import net.fabricmc.loom.util.ProjectTestTrait
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.security.DigestInputStream
import java.security.MessageDigest

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ReproducibleBuildTest extends Specification implements ProjectTestTrait {
	@Override
	String name() {
		"reproducible"
	}

	@Ignore //TODO this is currently failing!
	@Unroll
	def "build (gradle #gradle)"() {
		when:
			def result = create("build", gradle)
		then:
			result.task(":build").outcome == SUCCESS
			getOutputHash("fabric-example-mod-1.0.0.jar") == modHash
			getOutputHash("fabric-example-mod-1.0.0-sources.jar") == sourceHash
		where:
			gradle 				| modHash								| sourceHash
			'6.8.3' 			| "e1beb19574f3446800e0a5e289121365"	| "123"
			'7.0-milestone-2'	| "9759775e1f5440a18667c41cf1961908"	| "123"
	}

	String getOutputHash(String name) {
		generateMD5(getOutputFile(name))
	}

	String generateMD5(File file) {
		file.withInputStream {
			new DigestInputStream(it, MessageDigest.getInstance('MD5')).withStream {
				it.eachByte {}
				it.messageDigest.digest().encodeHex() as String
			}
		}
	}
}
