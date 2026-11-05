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

package net.fabricmc.loom.test.unit.fmj

import java.nio.file.Path

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.api.fmj.FabricModJsonV1Spec
import net.fabricmc.loom.task.FabricModJsonV1Task

class FabricModJsonV1TaskTest extends Specification {
	@TempDir
	Path tempDir

	def "worker writes the configured mod json"() {
		given:
		def project = ProjectBuilder.builder().build()
		def spec = project.objects.newInstance(FabricModJsonV1Spec)
		spec.modId.set("examplemod")
		spec.version.set("1.0.0")

		def output = tempDir.resolve("fabric.mod.json").toFile()
		def parameters = project.objects.newInstance(FabricModJsonV1Task.FabricModJsonV1WorkParameters)
		parameters.spec.set(spec)
		parameters.outputFile.set(output)
		def action = new FabricModJsonV1Task.FabricModJsonV1WorkAction() {
					@Override
					FabricModJsonV1Task.FabricModJsonV1WorkParameters getParameters() {
						return parameters
					}
				}

		when:
		action.execute()

		then:
		output.text == """
		{
		  "schemaVersion": 1,
		  "id": "examplemod",
		  "version": "1.0.0"
		}
		""".stripIndent().trim()
	}
}
