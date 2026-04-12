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

import java.nio.file.Files
import java.nio.file.Path

import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.providers.BundleMetadata
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.configuration.providers.minecraft.VersionsManifest
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadExecutor

class MinecraftJarMergerRunner {
	static Path dir = Path.of(".gradle", "test-files", "tomerge")

	static void main(String[] args) {
		def versionManifest = Download.create(Constants.VERSION_MANIFESTS)
				.downloadString()
		final VersionsManifest manifest = LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class)

		List<VersionInfo> versions = []
		// Download all the minecraft jars
		new DownloadExecutor(10).withCloseable {
			for (def version in manifest.versions()) {
				if (version.type() == "snapshot" && version.id() != "25w31a") {
					continue
				}

				if (version.id() == "1.2.5") {
					// Cannot merge any version before this.
					break
				}

				def info = downloadVersion(version, it)

				if (info != null) {
					versions.add(info)
				}
			}
		}

		for (def info in versions) {
			println("Merging version " + info.id)
			def mergedJar = dir.resolve(info.id + ".merged.jar")
			Files.deleteIfExists(mergedJar)

			def serverJar = info.serverJar.toFile()
			def bundleMetadata = BundleMetadata.fromJar(info.serverJar)

			if (bundleMetadata != null) {
				def unpackedJar = dir.resolve(info.id + ".unpacked.jar")
				bundleMetadata.versions().get(0)
						.unpackEntry(info.serverJar, unpackedJar, GradleTestUtil.mockProject())
				serverJar = unpackedJar.toFile()
			}

			def merger = new MinecraftJarMerger(info.clientJar.toFile(), serverJar, mergedJar.toFile())
			merger.merge()
		}
	}

	// Returns null if the version does not have a server jar
	static VersionInfo downloadVersion(VersionsManifest.Version version, DownloadExecutor downloadExecutor) {
		def manifest = Download.create(version.url())
				.sha1(version.sha1())
				.downloadString(dir.resolve(version.id() + ".json"))
		def meta = LoomGradlePlugin.GSON.fromJson(manifest, MinecraftVersionMeta.class)

		def client = meta.download("client")
		def server = meta.download("server")

		if (server == null) {
			return null
		}

		def clientJar = download(client, downloadExecutor)
		def serverJar = download(server, downloadExecutor)

		return new VersionInfo(
				id: version.id(),
				clientJar: clientJar,
				serverJar: serverJar
				)
	}

	static Path download(MinecraftVersionMeta.Download download, DownloadExecutor executor) {
		Path jarPath = dir.resolve(download.sha1() + ".jar")
		Download.create(download.url())
				.sha1(download.sha1())
				.downloadPathAsync(jarPath, executor)
		return jarPath
	}

	static class VersionInfo {
		String id
		Path clientJar
		Path serverJar
	}
}
