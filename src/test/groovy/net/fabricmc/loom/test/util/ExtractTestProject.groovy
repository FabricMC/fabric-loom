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

package net.fabricmc.loom.test.util

// A helper script to extract the test project from the resources and add the gradle wrapper
// Useful for testing an intergration test project in a real environment
class ExtractTestProject {
	static void main(String[] args) {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected one argument: the project name")
		}

		def projectName = args[0]
		def targetDirectory = new File("test/$projectName")
		def sourceDirectory = new File("src/test/resources/projects/$projectName")

		if (targetDirectory.exists()) {
			targetDirectory.deleteDir()
		}

		copyDir(sourceDirectory, targetDirectory)
		copyDir(new File("gradle"), new File(targetDirectory, "gradle"))
		copyFile(new File("gradlew"), new File(targetDirectory, "gradlew"))
		copyFile(new File("gradlew.bat"), new File(targetDirectory, "gradlew.bat"))

		new File(targetDirectory, "settings.gradle").text = """
            pluginManagement {
                repositories {
                    maven {
                        name = 'Fabric'
                        url = 'https://maven.fabricmc.net/'
                    }
                    mavenCentral()
                    gradlePluginPortal()
                    mavenLocal()
                }
            }
            """

		def buildGradle = new File(targetDirectory, "build.gradle")
		buildGradle.text = buildGradle.text.replace("id 'fabric-loom'", "id 'fabric-loom' version '1.8.local'")
	}

	private static void copyDir(File source, File target) {
		source.eachFileRecurse { file ->
			if (file.isDirectory()) {
				return
			}

			def relativePath = source.toPath().relativize(file.toPath())
			def targetFile = new File(target, relativePath.toString())
			copyFile(file, targetFile)
		}
	}

	private static void copyFile(File source, File target) {
		target.parentFile.mkdirs()
		target.bytes = source.bytes
		println("Copied $source to $target")
	}
}
