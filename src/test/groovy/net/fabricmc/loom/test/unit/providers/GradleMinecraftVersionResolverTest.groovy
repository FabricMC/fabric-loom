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

package net.fabricmc.loom.test.unit.providers

import org.gradle.api.Project

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.ConfigContextImpl
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.unit.download.DownloadTest
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.Constants

class GradleMinecraftVersionResolverTest extends DownloadTest {
	File testDir

	def setup() {
		testDir = LoomTestConstants.TEST_DIR.toPath().resolve("GradleMinecraftVersionResolverTest").toFile()
		testDir.deleteDir()
		testDir.mkdirs()

		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST)
		}
		server.get("/emptyVersionManifest") {
			it.result(EMPTY_VERSION_MANIFEST)
		}
	}

	def "range resolution uses normalized Minecraft ordering"() {
		setup:
		def project = project("net.minecraft:minecraft:[1.21.4-alpha.24.46.a,1.21.4]")

		when:
		def version = resolve(project)

		then:
		version == "1.21.4"
	}

	def "latest release ignores older snapshots"() {
		setup:
		def project = project("net.minecraft:minecraft:latest.release")

		when:
		def version = resolve(project)

		then:
		version == "1.21.4"
	}

	def "range resolution supports new snapshot format"() {
		setup:
		def project = project("net.minecraft:minecraft:[26.1-alpha.1,26.1-alpha.3]")

		when:
		def version = resolve(project)

		then:
		version == "26.1-snapshot-2"
	}

	def "direct unnormalizable version is accepted"() {
		setup:
		def project = project("net.minecraft:minecraft:this_is_not_semver")

		when:
		def version = resolve(project)

		then:
		version == "this_is_not_semver"
	}

	def "range endpoint using unnormalizable version fails"() {
		setup:
		def project = project("net.minecraft:minecraft:[this_is_not_semver,1.21.4]")

		when:
		resolve(project)

		then:
		def e = thrown(IllegalArgumentException)
		e.message == "Cannot use unnormalizable Minecraft version in a range: this_is_not_semver"
	}

	private static String resolve(Project project) {
		def extension = LoomGradleExtension.get(project)
		def context = new ConfigContextImpl(project, null, extension)
		return MinecraftMetadataProvider.create(context).getMinecraftVersion()
	}

	private Project project(String dependency) {
		def project = GradleTestUtil.realProject(new File(testDir, UUID.randomUUID().toString()))
		project.extensions.extraProperties.set("loom_version_manifests", "$PATH/versionManifest")
		project.extensions.extraProperties.set("loom_experimental_versions", "$PATH/emptyVersionManifest")
		project.pluginManager.apply(LoomGradlePlugin)
		project.dependencies.add(Constants.Configurations.MINECRAFT, dependency)
		return project
	}

	private static final String VERSION_MANIFEST = """
{
  "latest": {
    "release": "1.21.4",
    "snapshot": "24w46a"
  },
  "versions": [
    {
      "id": "24w46a",
      "type": "snapshot",
      "url": "https://example.invalid/24w46a.json"
    },
    {
      "id": "1.21.4",
      "type": "release",
      "url": "https://example.invalid/1.21.4.json"
    },
    {
      "id": "26.1-snapshot-2",
      "type": "snapshot",
      "url": "https://example.invalid/26.1-snapshot-2.json"
    },
    {
      "id": "this_is_not_semver",
      "type": "snapshot",
      "url": "https://example.invalid/this_is_not_semver.json"
    }
  ]
}
"""

	private static final String EMPTY_VERSION_MANIFEST = """
{
  "latest": {},
  "versions": []
}
"""
}
