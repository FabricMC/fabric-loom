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
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider
import net.fabricmc.loom.configuration.providers.minecraft.manifest.MinecraftVersionsManifestProvider
import net.fabricmc.loom.configuration.providers.minecraft.manifest.VersionMetadataService
import net.fabricmc.loom.configuration.providers.minecraft.manifest.VersionsManifestService;
import net.fabricmc.loom.configuration.providers.minecraft.manifest.VersionsManifest
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.unit.LoomMocks
import net.fabricmc.loom.test.unit.download.DownloadTest
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.download.Download

class MinecraftVersionMetadataProviderTest extends DownloadTest {
	Path testDir

	def setup() {
		testDir = LoomTestConstants.TEST_DIR.toPath().resolve("MinecraftVersionMetadataProviderTest")

		testDir.toFile().deleteDir()
		Files.createDirectories(testDir)
	}

	def "Download versions manifest"() {
		setup:
		server.get("/versionsManifest") {
			it.result(VERSIONS_MANIFEST_1)
		}

		when:
		def manifest = versionsManifest("$PATH/versionsManifest")

		then:
		manifest.versions().size() == 1
	}

	def "Download experimental versions manifest"() {
		setup:
		server.get("/versionsManifest") {
			it.result(VERSIONS_MANIFEST_1)
		}

		when:
		def manifest = experimentalVersionsManifest("$PATH/versionsManifest", "$PATH/experimentalVersionsManifest")

		then:
		manifest.versions().size() == 1
	}

	def "Download version metadata"(){
		setup:
		server.get("/versionsManifest") {
			it.result(VERSIONS_MANIFEST_1)
		}

		when:
		def manifest = versionsManifest("$PATH/versionsManifest")
		def metadata = versionMetadata("1.20.1", null, manifest)

		then:
		metadata.id() == "1.20.1"
	}

	def "Download experimental version metadata"(){
		setup:
		server.get("/versionsManifest") {
			it.result(VERSIONS_MANIFEST_1)
		}
		server.get("/experimentalVersionsManifest") {
			it.result(EXP_VERSIONS_MANIFEST)
		}

		when:
		def manifest = versionsManifest("$PATH/versionsManifest")
		def expManifest = experimentalVersionsManifest("$PATH/versionsManifest", "$PATH/experimentalVersionsManifest")
		def metadata = versionMetadata("1.19_deep_dark_experimental_snapshot-1", null, manifest, expManifest)

		then:
		metadata.id() == "1.19_deep_dark_experimental_snapshot-1"
	}

	def "Download custom version metadata"() {
		setup:
		server.get("/customVersionMetadata") {
			it.result('{"id": "2.0.0"}')
		}

		when:
		def metadata = versionMetadata("2.0.0", "$PATH/customVersionMetadata")

		then:
		metadata.id() == "2.0.0"
	}

	def "Download unknown version metadata"() {
		setup:
		server.get("/versionsManifest") {
			it.result(VERSIONS_MANIFEST_1)
		}
		server.get("/experimentalVersionsManifest") {
			it.result(EXP_VERSIONS_MANIFEST)
		}

		when:
		def manifest = versionsManifest("$PATH/versionsManifest")
		def expManifest = experimentalVersionsManifest("$PATH/versionsManifest", "$PATH/experimentalVersionsManifest")
		def metadata = versionMetadata("2.0", null, manifest, expManifest)

		then:
		def e = thrown(RuntimeException)
		e.message == "Failed to find minecraft version: 2.0.0"
	}

	private VersionsManifestService manifestService(String manifestUrl, String experimentalManifestUrl = null) {
		return VersionsManifestService.create(LoomMocks.minecraftVersionsManifestProviderMock(manifestUrl, experimentalManifestUrl), GradleTestUtil.mockLoomGradleExtension(), null);
	}

	private VersionsManifest versionsManifest(String manifestUrl, String experimentalManifestUrl = null) {
		return manifestService(manifestUrl, experimentalManifestUrl).versionsManifest;
	}

	private VersionsManifest experimentalVersionsManifest(String manifestUrl, String experimentalManifestUrl = null) {
		return manifestService(manifestUrl, experimentalManifestUrl).experimentalVersionsManifest;
	}

	private VersionMetadataService metadataService(String minecraftVersion, String metadataUrl, VersionsManifest manifest = null, VersionsManifest experimentalManifest = null) {
		return VersionMetadataService.create(LoomMocks.minecraftVersionMetadataProviderMock(minecraftVersion, metadataUrl, manifest, experimentalManifest), Mock(MinecraftProvider), null);
	}

	private VersionsManifest versionMetadata(String minecraftVersion, String metadataUrl, VersionsManifest manifest = null, VersionsManifest experimentalManifest = null) {
		return metadataService(minecraftVersion, metadataUrl, manifest, experimentalManifest).versionMetadata;
	}

	private static final String VERSIONS_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

	@Language("json")
	private static final String VERSIONS_MANIFEST_1 = """
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
	private static final String VERSIONS_MANIFEST_2 = """
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
	private static final String EXP_VERSIONS_MANIFEST = """
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
