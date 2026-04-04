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

package net.fabricmc.loom.test.util

import java.nio.file.Path

import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.providers.BundleMetadata
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.configuration.providers.minecraft.VersionsManifest
import net.fabricmc.loom.configuration.providers.minecraft.verify.CertificateChain
import net.fabricmc.loom.configuration.providers.minecraft.verify.JarVerifier
import net.fabricmc.loom.configuration.providers.minecraft.verify.KnownVersions
import net.fabricmc.loom.configuration.providers.minecraft.verify.SignatureVerificationFailure
import net.fabricmc.loom.util.Checksum
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadExecutor

/**
 * A quick and dirty script to generate the known_versions.json file
 * This file contains a list of all the unsigned versions of Minecraft and their sha1 hashes
 * Note: Running this will take a while as it downloads all the versions of Minecraft
 */
class KnownVersionsGenerator {
	static Path dir = Path.of(".gradle", "test-files", "unsigned")
	static CertificateChain chain = CertificateChain.getRoot("mojangcs")

	static void main(String[] args) {
		def versionManifest = Download.create(Constants.VERSION_MANIFESTS)
				.downloadString()
		final VersionsManifest manifest = LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class)

		// Download all the minecraft jars
		new DownloadExecutor(10).withCloseable {
			for (def version in manifest.versions()) {
				downloadVersion(version, it)
			}
		}

		println("Downloaded all versions")

		def unsignedClientVersions = [:] as Map<String, String>
		def unsignedServerVersions = [:] as Map<String, String>

		for (def version in manifest.versions()) {
			println("Checking version " + version.id())
			checkVersion(version, unsignedClientVersions, unsignedServerVersions)
		}

		def json = LoomGradlePlugin.GSON.toJson(new KnownVersions(unsignedClientVersions, unsignedServerVersions))
		println(json)
	}

	static void downloadVersion(VersionsManifest.Version version, DownloadExecutor downloadExecutor) {
		def manifest = Download.create(version.url())
				.sha1(version.sha1())
				.downloadString(dir.resolve(version.id() + ".json"))
		def meta = LoomGradlePlugin.GSON.fromJson(manifest, MinecraftVersionMeta.class)

		def client = meta.download("client")
		def server = meta.download("server")

		download(client, downloadExecutor)

		if (server != null) {
			download(server, downloadExecutor)
		}
	}

	static void download(MinecraftVersionMeta.Download download, DownloadExecutor executor) {
		Path jarPath = dir.resolve(download.sha1() + ".jar")
		Download.create(download.url())
				.sha1(download.sha1())
				.downloadPathAsync(jarPath, executor)
	}

	static void checkVersion(VersionsManifest.Version version, Map<String, String> unsignedClientVersions, Map<String, String> unsignedServerVersions) {
		def manifest = Download.create(version.url())
				.sha1(version.sha1())
				.downloadString(dir.resolve(version.id() + ".json"))
		def meta = LoomGradlePlugin.GSON.fromJson(manifest, MinecraftVersionMeta.class)

		def client = meta.download("client")
		def server = meta.download("server")

		def clientJar = dir.resolve(client.sha1() + ".jar")

		if (!isSigned(clientJar)) {
			unsignedClientVersions.put(version.id(), Checksum.of(clientJar).sha256().hex())
		}

		if (server != null) {
			def serverJar = dir.resolve(server.sha1() + ".jar")
			if (BundleMetadata.fromJar(serverJar) == null) {
				unsignedServerVersions.put(version.id(), Checksum.of(serverJar).sha256().hex())
			}
		}
	}

	static boolean isSigned(Path jarPath) {
		try {
			JarVerifier.verify(jarPath, chain)
			return true
		} catch (SignatureVerificationFailure ignored) {
			return false
		}
	}
}
