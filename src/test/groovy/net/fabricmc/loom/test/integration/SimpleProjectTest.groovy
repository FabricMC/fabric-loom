/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

import net.fabricmc.loom.test.util.ArchiveAssertionsTrait
import net.fabricmc.loom.test.util.ProjectTestTrait
import net.fabricmc.loom.test.util.ServerRunner
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SimpleProjectTest extends Specification implements ProjectTestTrait, ArchiveAssertionsTrait {
	@Override
	String name() {
		"simple"
	}

	@Unroll
	def "build and run (gradle #gradle)"() {
		given:
			def server = ServerRunner.create(testProjectDir, "1.16.5")
										.withMod(getFutureOutputFile("fabric-example-mod-1.0.0.jar"))
										.withFabricApi()
		when:
			def result = create("build", gradle)
			def serverResult = server.run()
		then:
			result.task(":build").outcome == SUCCESS
			getArchiveEntry("fabric-example-mod-1.0.0.jar", "META-INF/MANIFEST.MF").contains("Fabric-Loom-Version: 0.0.0+unknown")

			serverResult.successful()
			serverResult.output.contains("Hello simple Fabric mod") // A check to ensure our mod init was actually called
		where:
			gradle              | _
			DEFAULT_GRADLE      | _
			PRE_RELEASE_GRADLE  | _
	}

	@Unroll
	def "#ide config generation"() {
		when:
			def result = create(ide)
		then:
			result.task(":${ide}").outcome == SUCCESS
		where:
			ide 		| _
			'idea' 		| _
			'eclipse'	| _
			'vscode'	| _
	}
}
