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

import java.nio.file.Path

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.LoomGradlePlugin
import net.fabricmc.loom.configuration.providers.BundleMetadata
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.configuration.providers.minecraft.VersionsManifest
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadExecutor

class MinecraftJarMergerTest extends Specification {
	private static final Path dir = LoomTestConstants.TEST_DIR.toPath().resolve("jar-merger")

	@TempDir
	Path tempDir

	def "25w31a"() {
		setup:
		def jars = prepareJars("25w31a")
		def out = tempDir.resolve("25w31a.merged.jar")

		when:
		def merger = new MinecraftJarMerger(jars.clientJar.toFile(), jars.serverJar.toFile(), out.toFile())
		merger.merge()

		then:
		methodAccess(out, "net/minecraft/server/MinecraftServer", "v", "()Ljk;") == Opcodes.ACC_PROTECTED
	}

	def "1.13.2"() {
		setup:
		def jars = prepareJars("1.13.2")
		def out = tempDir.resolve("1.13.2.merged.jar")

		when:
		def merger = new MinecraftJarMerger(jars.clientJar.toFile(), jars.serverJar.toFile(), out.toFile())
		merger.merge()

		then:
		methodAccess(out, "net/minecraft/server/MinecraftServer", "a", "(Z)V") == Opcodes.ACC_PROTECTED
		fieldAccess(out, "net/minecraft/server/MinecraftServer", "f", "Ljava/util/Queue;") == (Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL)
	}

	static int methodAccess(Path jar, String owner, String name, String desc) {
		return getClassNode(jar, owner).methods.find { it.name == name && it.desc == desc }.access
	}

	static int fieldAccess(Path jar, String owner, String name, String desc) {
		return getClassNode(jar, owner).fields.find { it.name == name && it.desc == desc }.access
	}

	static ClassNode getClassNode(Path jar, String owner) {
		byte[] data = ZipUtils.unpack(jar, "${owner}.class")
		ClassReader reader = new ClassReader(data)
		ClassNode node = new ClassNode(Constants.ASM_VERSION)
		reader.accept(node, 0)
		return node
	}

	static Jars prepareJars(String id) {
		def jars = downloadJars(id)

		def bundleMetadata = BundleMetadata.fromJar(jars.serverJar)

		if (bundleMetadata == null) {
			return jars
		}

		def unpackedJar = dir.resolve(id + ".unpacked.jar")

		bundleMetadata.versions().get(0)
				.unpackEntry(jars.serverJar, unpackedJar, GradleTestUtil.mockProject())

		return new Jars(
				clientJar: jars.clientJar,
				serverJar: unpackedJar
				)
	}

	static Jars downloadJars(String id) {
		def manifestJson = Download.create(Constants.VERSION_MANIFESTS)
				.downloadString()
		def manifest = LoomGradlePlugin.GSON.fromJson(manifestJson, VersionsManifest.class)
		def version = manifest.getVersion(id)

		new DownloadExecutor(2).withCloseable {
			return downloadVersion(version, it)
		}
	}

	static Jars downloadVersion(VersionsManifest.Version version, DownloadExecutor downloadExecutor) {
		def manifest = Download.create(version.url)
				.sha1(version.sha1)
				.downloadString(dir.resolve(version.id + ".json"))
		def meta = LoomGradlePlugin.GSON.fromJson(manifest, MinecraftVersionMeta.class)

		def client = meta.download("client")
		def server = meta.download("server")

		if (server == null) {
			return null
		}

		def clientJar = download(client, downloadExecutor)
		def serverJar = download(server, downloadExecutor)

		return new Jars(
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

	static class Jars {
		Path clientJar
		Path serverJar
	}
}
