/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.configuration.DebofInstallerData

class InstallerDataTest extends Specification {
	@TempDir
	Path directory

	def "Fabric Loader filenames are checked before other installers"() {
		given:
		def other = installer("other.jar", "other")
		def loader = installer("fabric-loader-1.0.jar", "loader")
		def secondLoader = installer("fabric-loader-2.0.jar", "second-loader")
		def malformed = archive("malformed.jar", ["fabric-installer.json": "{}"])

		expect:
		DebofInstallerData.findInstaller([
			malformed,
			other,
			loader,
			secondLoader
		] as LinkedHashSet).get().version() == "loader"
		DebofInstallerData.findInstaller([other, secondLoader, loader] as LinkedHashSet).get().version() == "second-loader"
	}

	def "Loader filenames without installers fall back to the original file order"() {
		given:
		def first = installer("first.jar", "first")
		def second = installer("second.jar", "second")
		def absent = archive("fabric-loader-absent.jar", [:])

		expect:
		DebofInstallerData.findInstaller([first, absent, second] as LinkedHashSet).get().version() == "first"
		DebofInstallerData.findInstaller([second, absent, first] as LinkedHashSet).get().version() == "second"
	}

	def "No installer is found when all candidate archives lack installer data"() {
		given:
		def loader = archive("fabric-loader-absent.jar", [:])
		def other = archive("other.jar", [:])

		expect:
		DebofInstallerData.findInstaller([loader, other] as LinkedHashSet).isEmpty()
	}

	private File installer(String name, String version) {
		archive(name, ["fabric-installer.json": '{}', "fabric.mod.json": "{\"id\":\"test\",\"version\":\"${version}\"}"])
	}

	private File archive(String name, Map<String, String> entries) {
		def path = directory.resolve(name)

		new ZipOutputStream(Files.newOutputStream(path)).withCloseable { zip ->
			entries.each { entry, content ->
				zip.putNextEntry(new ZipEntry(entry))
				zip.write(content.getBytes(StandardCharsets.UTF_8))
				zip.closeEntry()
			}
		}

		path.toFile()
	}
}
