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

import java.nio.file.Path

import spock.lang.Specification

import net.fabricmc.loom.configuration.mods.ArtifactMetadata
import net.fabricmc.loom.configuration.mods.ArtifactRef

import static net.fabricmc.loom.configuration.mods.ArtifactMetadata.MixinRemapType.MIXIN
import static net.fabricmc.loom.configuration.mods.ArtifactMetadata.MixinRemapType.STATIC
import static net.fabricmc.loom.configuration.mods.ArtifactMetadata.RemapRequirements.*
import static net.fabricmc.loom.test.util.ZipTestUtils.createZip
import static net.fabricmc.loom.test.util.ZipTestUtils.manifest

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

	def "Refmap remap type" () {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		def result = metadata.mixinRemapType()
		then:
		result == type
		where:
		type | entries
		MIXIN       | ["hello.json": "{}"] 												// None Mod jar
		MIXIN       | ["fabric.mod.json": "{}"] 										// Fabric mod without manfiest file
		MIXIN       | ["fabric.mod.json": "{}", "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Mixin-Remap-Type", "mixin")] 	// Fabric mod without remap type entry
		STATIC  	| ["fabric.mod.json": "{}", "META-INF/MANIFEST.MF": manifest("Fabric-Loom-Mixin-Remap-Type", "static")]	// Fabric mod opt-in
	}

	// Test that a mod with the same or older version of loom can be read
	def "Valid loom version"() {
		given:
		def zip = createMod(modLoomVersion, "mixin")
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		metadata != null
		where:
		loomVersion | modLoomVersion
		"1.4"       | "1.0.1"
		"1.4"       | "1.0.99"
		"1.4"       | "1.4"
		"1.4"       | "1.4.0"
		"1.4"       | "1.4.1"
		"1.4"       | "1.4.99"
		"1.4"       | "1.4.local"
		"1.5"		| "1.4.99"
		"2.0"		| "1.4.99"
	}

	// Test that a mod with the same or older version of loom can be read
	def "Invalid loom version"() {
		given:
		def zip = createMod(modLoomVersion, "mixin")
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		def e = thrown(IllegalStateException)
		e.message == "Mod was built with a newer version of Loom ($modLoomVersion), you are using Loom ($loomVersion)"
		where:
		loomVersion | modLoomVersion
		"1.4"       | "1.5"
		"1.4"       | "1.5.00"
		"1.4"       | "2.0"
		"1.4"       | "2.4"
	}

	def "Accepts all Loom versions"() {
		given:
		def zip = createMod(modLoomVersion, "static")
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		metadata != null
		where:
		loomVersion | modLoomVersion
		// Valid
		"1.4"       | "1.0.1"
		"1.4"       | "1.0.99"
		"1.4"       | "1.4"
		"1.4"       | "1.4.0"
		"1.4"       | "1.4.1"
		"1.4"       | "1.4.99"
		"1.4"       | "1.4.local"
		"1.5"		| "1.4.99"
		"2.0"		| "1.4.99"
		// Usually invalid
		"1.4"       | "1.5"
		"1.4"       | "1.5.00"
		"1.4"       | "2.0"
		"1.4"       | "2.4"
	}

	private static Path createMod(String loomVersion, String remapType) {
		return createZip(["fabric.mod.json": "{}", "META-INF/MANIFEST.MF": manifest(["Fabric-Loom-Version": loomVersion, "Fabric-Loom-Mixin-Remap-Type": remapType])])
	}

	private static ArtifactMetadata createMetadata(Path zip, String loomVersion = "1.4") {
		return ArtifactMetadata.create(createArtifact(zip), loomVersion)
	}

	private static ArtifactRef createArtifact(Path zip) {
		return new ArtifactRef.FileArtifactRef(zip, "net.fabric", "loom-test", "1.0")
	}
}
