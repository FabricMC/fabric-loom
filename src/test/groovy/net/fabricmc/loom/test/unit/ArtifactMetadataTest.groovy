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

package net.fabricmc.loom.test.unit

import net.fabricmc.loom.configuration.mods.ArtifactMetadata
import net.fabricmc.loom.configuration.mods.ArtifactRef
import net.fabricmc.loom.util.FileSystemUtil
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest

import static net.fabricmc.loom.configuration.mods.ArtifactMetadata.RemapRequirements.*

class ArtifactMetadataTest extends Specification {
	def "is fabric mod"() {
		given:
			def zip = createZip(entries)
		when:
			def metadata = createMetadata(zip)
		then:
			isMod == metadata.isFabricMod()
		where:
			isMod 		| entries
			false       | ["hello.json": "{}"] 		// None Mod jar
			true        | ["fabric.mod.json": "{}"] // Fabric mod
	}

	def "remap requirements"() {
		given:
			def zip = createZip(entries)
		when:
			def metadata = createMetadata(zip)
		then:
			requirements == metadata.remapRequirements()
		where:
			requirements | entries
			DEFAULT      | ["fabric.mod.json": "{}"] 										// Default
			OPT_OUT      | ["META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "false")] // opt-out
			OPT_IN       | ["META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "true")]	// opt-in
	}

	def "Should Remap" () {
		given:
			def zip = createZip(entries)
		when:
			def metadata = createMetadata(zip)
			def result = metadata.shouldRemap()
		then:
			result == shouldRemap
		where:
			shouldRemap | entries
			false       | ["hello.json": "{}"] 												// None Mod jar
			true        | ["fabric.mod.json": "{}"] 										// Fabric mod
			false       | ["fabric.mod.json": "{}",
						   "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "false")] 	// Fabric mod opt-out
			true        | ["fabric.mod.json": "{}",
						   "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "true")]	// Fabric mod opt-in
			false       | ["hello.json": "{}",
						   "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "false")]	// None opt-out
			true        | ["hello.json": "{}",
						   "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "true")]	// None opt-int
			false        | ["hello.json": "{}",
					   		"META-INF/MANIFEST.MF": manifest("Fabric-Loom-Remap", "broken")]// Invalid format
			false        | ["hello.json": "{}",
							"META-INF/MANIFEST.MF": manifest("Something", "Hello")]			// Invalid format
	}

	def "Installer data"() {
		given:
			def zip = createZip(entries)
		when:
			def metadata = createMetadata(zip)
		then:
			isLoader == (metadata.installerData() != null)
		where:
			isLoader   | entries
			true       | ["fabric.mod.json": "{}", "fabric-installer.json": "{}"] // Fabric mod, with installer data
			false      | ["fabric.mod.json": "{}"] // Fabric mod, no installer data
	}

	private static ArtifactMetadata createMetadata(Path zip) {
		return ArtifactMetadata.create(createArtifact(zip))
	}

	private static ArtifactRef createArtifact(Path zip) {
		return new ArtifactRef.FileArtifactRef(zip, "net.fabric", "loom-test", "1.0")
	}

	private static Path createZip(Map<String, String> entries) {
		def file = Files.createTempFile("loom-test", ".zip")
		Files.delete(file)

		FileSystemUtil.getJarFileSystem(file, true).withCloseable { zip ->
			entries.forEach { path, value ->
				def fsPath = zip.getPath(path)
				def fsPathParent = fsPath.getParent()
				if (fsPathParent != null) Files.createDirectories(fsPathParent)
				Files.writeString(fsPath, value, StandardCharsets.UTF_8)
			}
		}

		return file
	}

	private String manifest(String key, String value) {
		def manifest = new Manifest()
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
		manifest.getMainAttributes().putValue(key, value)

		def out = new ByteArrayOutputStream()
		manifest.write(out)
		return out.toString(StandardCharsets.UTF_8)
	}
}
