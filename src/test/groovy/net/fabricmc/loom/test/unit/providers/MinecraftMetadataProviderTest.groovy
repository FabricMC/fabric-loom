/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.unit.providers

import java.nio.file.Files
import java.nio.file.Path

import org.intellij.lang.annotations.Language

import net.fabricmc.loom.configuration.providers.minecraft.ManifestLocations
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.unit.download.DownloadTest
import net.fabricmc.loom.util.download.Download

class MinecraftMetadataProviderTest extends DownloadTest {
	Path testDir

	def setup() {
		testDir = LoomTestConstants.TEST_DIR.toPath().resolve("MinecraftMetadataProviderTest")

		testDir.toFile().deleteDir()
		Files.createDirectories(testDir)
	}

	def "Cached result"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}

		when:
		// Test the builtin caching of the metadata provider
		def metaProvider = provider("1.20.1")
		metaProvider.getVersionMeta()
		def meta = metaProvider.getVersionMeta()

		// Create a new provider to test download caching
		def meta2 = provider("1.20.1").getVersionMeta()

		then:
		meta.id() == "1.20.1"
		meta2.id() == "1.20.1"
		calls == 1
	}

	// Tests a fix to https://github.com/FabricMC/fabric-loom/issues/886
	def "Force download with new version"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(calls == 0 ? VERSION_MANIFEST_1 : VERSION_MANIFEST_2)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		def meta = provider("1.20.1").getVersionMeta()
		def meta2 = provider("1.20.1-rc1").getVersionMeta()

		then:
		meta.id() == "1.20.1"
		meta2.id() == "1.20.1-rc1"
		calls == 3
	}

	def "Experimental"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		def meta = provider("1.19_deep_dark_experimental_snapshot-1").getVersionMeta()

		then:
		meta.id() == "1.19_deep_dark_experimental_snapshot-1"
		calls == 2
	}

	def "Custom manifest"() {
		setup:
		server.get("/customManifest") {
			it.result('{"id": "2.0.0"}')
		}

		when:
		def meta = provider("2.0.0", "$PATH/customManifest").getVersionMeta()

		then:
		meta.id() == "2.0.0"
	}

	def "Get unknown"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		provider("2.0.0").getVersionMeta()

		then:
		def e = thrown(RuntimeException)
		e.message == "Failed to find minecraft version: 2.0.0"
		calls == 4
	}

	private MinecraftMetadataProvider provider(String version, String customUrl = null) {
		return new MinecraftMetadataProvider(
				options(version, customUrl),
				Download.&create
				)
	}

	private MinecraftMetadataProvider.Options options(String version, String customUrl) {
		ManifestLocations manifests = new ManifestLocations("versions_manifest")
		manifests.addBuiltIn(0, "$PATH/versionManifest", "versions_manifest")
		manifests.addBuiltIn(1, "$PATH/experimentalVersionManifest", "experimental_versions_manifest")

		return new MinecraftMetadataProvider.Options(
				version,
				manifests,
				customUrl,
				testDir,
				testDir
				)
	}

	@Language("json")
	private static final String VERSION_MANIFEST_1 = """
{
  "latest": {
    "release": "1.20.1",
    "snapshot": "1.20.1"
  },
  "versions": [
    {
      "id": "1.20.1",
      "url": "https://piston-meta.mojang.com/v1/packages/715ccf3330885e75b205124f09f8712542cbe7e0/1.20.1.json",
      "sha1": "715ccf3330885e75b205124f09f8712542cbe7e0"
    }
  ]
}
"""

	@Language("json")
	private static final String VERSION_MANIFEST_2 = """
{
  "latest": {
    "release": "1.20.1",
    "snapshot": "1.20.1"
  },
  "versions": [
    {
      "id": "1.20.1",
      "url": "https://piston-meta.mojang.com/v1/packages/715ccf3330885e75b205124f09f8712542cbe7e0/1.20.1.json",
      "sha1": "715ccf3330885e75b205124f09f8712542cbe7e0"
    },
    {
      "id": "1.20.1-rc1",
      "url": "https://piston-meta.mojang.com/v1/packages/61c85d1e228b4ca6e48d2da903d2399c12b6a880/1.20.1-rc1.json",
      "sha1": "61c85d1e228b4ca6e48d2da903d2399c12b6a880"
    }
  ]
}
"""
	@Language("json")
	private static final String EXP_VERSION_MANIFEST = """
{
  "latest": {
    "release": "1.19_deep_dark_experimental_snapshot-1",
    "snapshot": "1.19_deep_dark_experimental_snapshot-1"
  },
  "versions": [
    {
      "id": "1.19_deep_dark_experimental_snapshot-1",
      "url": "https://maven.fabricmc.net/net/minecraft/1_19_deep_dark_experimental_snapshot-1.json",
      "sha1": "c5b59acb75db612cf446b4ed4bd59b01e10092d1"
    }
  ]
}
"""
}
