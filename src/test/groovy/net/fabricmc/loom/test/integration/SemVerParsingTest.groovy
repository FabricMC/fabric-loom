/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.test.integration

import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.build.nesting.NestableJarGenerationTask
import net.fabricmc.loom.test.util.GradleProjectTestTrait

class SemVerParsingTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "test valid Semantic Versioning strings"() {
		expect:
		NestableJarGenerationTask.validSemVer(version) == true

		where:
		version                   | _
		"1.0.0"                   | _
		"2.5.3"                   | _
		"3.0.0-beta.2"            | _
		"4.2.1-alpha+001"         | _
		"5.0.0-rc.1+build.1"      | _
	}

	@Unroll
	def "test non-Semantic Versioning strings"() {
		expect:
		NestableJarGenerationTask.validSemVer(version) == false

		where:
		version                   | _
		"1.0"                     | _
		"3.0.0.Beta1-120922-126"  | _
		"3.0.2.Final"             | _
		"4.2.1.4.RELEASE"         | _
	}

	@Unroll
	def "test '.Final' suffixed SemVer"() {
		expect:
		NestableJarGenerationTask.getVersion(metadata) == expectedVersion

		where:
		metadata                                                               | expectedVersion
		new NestableJarGenerationTask.Metadata("group", "name", "1.0.0.Final", null)  | "1.0.0"
		new NestableJarGenerationTask.Metadata("group", "name", "2.5.3.final", null)  | "2.5.3"
	}
}
