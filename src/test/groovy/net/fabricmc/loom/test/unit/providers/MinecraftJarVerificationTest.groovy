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

package net.fabricmc.loom.test.unit.providers

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification

import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.providers.BundleMetadata
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.configuration.providers.minecraft.VersionsManifest
import net.fabricmc.loom.configuration.providers.minecraft.verify.MinecraftJarVerification
import net.fabricmc.loom.configuration.providers.minecraft.verify.SignatureVerificationFailure
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.download.Download

class MinecraftJarVerificationTest extends Specification {
	static Path dir = Path.of(".gradle", "test-files", "jar-verification")

	def "check client verified"() {
		setup:
		def jar = getJar(version, "client")
		def project = GradleTestUtil.mockProject()
		def verification = project.objects.newInstance(MinecraftJarVerification.class, version)

		when:
		verification.verifyClientJar(jar)

		then:
		true == true

		where:
		version  | _
		"1.21.5" | _
		"1.16.5" | _
		"1.14.4" | _
		"1.7.10" | _
		"1.7.9"  | _ // Sha1 signed
		"b1.5"   | _ // Not signed
	}

	def "check bundled server verified"() {
		setup:
		def jar = getJar(version, "server")
		def unpackedJar = jar.resolveSibling(jar.fileName.toString() + ".unpacked")
		def project = GradleTestUtil.mockProject()
		def verification = project.objects.newInstance(MinecraftJarVerification.class, version)
		def bundle = BundleMetadata.fromJar(jar)
		bundle.versions().get(0).unpackEntry(jar, unpackedJar, project)

		when:
		verification.verifyServerJar(unpackedJar)

		then:
		true == true

		where:
		version  | _
		"1.21.5" | _
	}

	def "check standalone server verified"() {
		setup:
		def jar = getJar(version, "server")
		def project = GradleTestUtil.mockProject()
		def verification = project.objects.newInstance(MinecraftJarVerification.class, version)

		when:
		verification.verifyServerJar(jar)

		then:
		true == true

		where:
		version  | _
		"1.16.5" | _
		"1.14.4" | _
		"1.7.10" | _
		"1.7.9"  | _ // Sha1 signed
		"1.2.5"  | _
	}

	def "hash mismatch"() {
		setup:
		def jar = getJar("1.2.5", "client")
		def project = GradleTestUtil.mockProject()
		def verification = project.objects.newInstance(MinecraftJarVerification.class, "1.2.4")

		when:
		verification.verifyClientJar(jar)

		then:
		thrown SignatureVerificationFailure
	}

	def "unverified jar"() {
		setup:
		Path tempDir = Files.createTempDirectory("test")
		def jar = tempDir.resolve("client.jar")
		ZipUtils.add(jar, "hello.txt", "Hello World".bytes)

		def project = GradleTestUtil.mockProject()
		def verification = project.objects.newInstance(MinecraftJarVerification.class, "blah")

		when:
		verification.verifyClientJar(jar)

		then:
		thrown SignatureVerificationFailure
	}

	private static Path getJar(String id, String type) {
		def versionManifest = Download.create(Constants.VERSION_MANIFESTS)
				.downloadString(dir.resolve("manifest.json"))
		final VersionsManifest versions = LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class)

		def version = versions.getVersion(id)
		def manifest = Download.create(version.url())
				.sha1(version.sha1())
				.downloadString(dir.resolve(version.id() + ".json"))
		def meta = LoomGradlePlugin.GSON.fromJson(manifest, MinecraftVersionMeta.class)

		def download = meta.download(type)
		Path jarPath = dir.resolve(download.sha1() + ".jar")
		Download.create(download.url())
				.sha1(download.sha1())
				.downloadPath(jarPath)

		return jarPath
	}
}
