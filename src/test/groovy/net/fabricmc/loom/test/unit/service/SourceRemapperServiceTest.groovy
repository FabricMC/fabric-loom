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

package net.fabricmc.loom.test.unit.service

import java.nio.file.Files
import java.nio.file.Path

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.intellij.lang.annotations.Language

import net.fabricmc.loom.task.service.MappingsService
import net.fabricmc.loom.task.service.SourceRemapperService
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.DeletingFileVisitor

class SourceRemapperServiceTest extends ServiceTestBase {
	def "remap sources"() {
		given:
		Path tempDirectory = Files.createTempDirectory("test")
		Path sourceDirectory = tempDirectory.resolve("source")
		Path destDirectory = tempDirectory.resolve("dst")
		Path mappings = tempDirectory.resolve("mappings.tiny")

		Files.createDirectories(sourceDirectory)
		Files.createDirectories(destDirectory)
		Files.writeString(sourceDirectory.resolve("Source.java"), SOURCE)
		Files.writeString(mappings, MAPPINGS)

		SourceRemapperService service = factory.get(new TestOptions(
				mappings: GradleTestUtil.mockProperty(
				new MappingsServiceTest.TestOptions(
				mappingsFile: GradleTestUtil.mockRegularFileProperty(mappings.toFile()),
				from: GradleTestUtil.mockProperty("named"),
				to: GradleTestUtil.mockProperty("intermediary"),
				)
				),
				))

		when:
		service.remapSourcesJar(sourceDirectory, destDirectory)

		then:
		// This isn't actually remapping, as we dont have the classpath setup at all. But that's not what we're testing here.
		!Files.readString(destDirectory.resolve("Source.java")).isEmpty()

		cleanup:
		Files.walkFileTree(tempDirectory, new DeletingFileVisitor())
	}

	@Language("java")
	static String SOURCE = """
        class Source {
            public void test() {
                System.out.println("Hello");
            }
        }
    """.trim()

	// Tiny v2 mappings to rename println
	static String MAPPINGS = """
    tiny	2	0	intermediary	named
    c	Source Source
    	m	()V	println	test
    """.trim()

	static class TestOptions implements SourceRemapperService.Options {
		Property<MappingsService.Options> mappings
		Property<Integer> javaCompileRelease = GradleTestUtil.mockProperty(17)
		ConfigurableFileCollection classpath = GradleTestUtil.mockConfigurableFileCollection()
		Property<String> serviceClass = serviceClassProperty(SourceRemapperService.TYPE)
	}
}
