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

import net.fabricmc.loom.configuration.providers.minecraft.verify.CertificateChain
import net.fabricmc.loom.configuration.providers.minecraft.verify.JarVerifier
import net.fabricmc.loom.configuration.providers.minecraft.verify.SignatureVerificationFailure
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.download.Download

class JarVerifierTest extends Specification {
	public static final String CLIENT_JAR_URL = "https://launcher.mojang.com/v1/objects/7e46fb47609401970e2818989fa584fd467cd036/client.jar"
	public static final String INSTALLER_JAR_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar"
	public static final File mcJarDir = new File(LoomTestConstants.TEST_DIR, "jar-verifier")

	def "verify Minecraft Jar"() {
		setup:
		def clientJar = downloadJarIfNotExists(CLIENT_JAR_URL, "client.jar")
		def cert = CertificateChain.getRoot("mojangcs")
		when:
		JarVerifier.verify(clientJar, cert)
		then:
		true == true
	}

	def "invalid Minecraft Jar, extra entry"() {
		setup:
		def clientJar = downloadJarIfNotExists(CLIENT_JAR_URL, "client.jar")
		Path tempDir = Files.createTempDirectory("test")
		def tempJar = tempDir.resolve("client.jar")
		Files.copy(clientJar, tempJar)

		ZipUtils.add(tempJar, "extra.txt", "Hello World".bytes)

		def cert = CertificateChain.getRoot("mojangcs")
		when:
		JarVerifier.verify(tempJar, cert)
		then:
		def e = thrown SignatureVerificationFailure
		e.message == "Jar entry extra.txt does not have a signature"
	}

	def "invalid Minecraft Jar, modified entry"() {
		setup:
		def clientJar = downloadJarIfNotExists(CLIENT_JAR_URL, "client.jar")
		Path tempDir = Files.createTempDirectory("test")
		def tempJar = tempDir.resolve("client.jar")
		Files.copy(clientJar, tempJar)

		ZipUtils.replace(tempJar, "version.json", "Hello World".bytes)

		def cert = CertificateChain.getRoot("mojangcs")
		when:
		JarVerifier.verify(tempJar, cert)
		then:
		def e = thrown SignatureVerificationFailure
		e.message == "Jar entry version.json failed signature verification"
	}

	def "invalid Minecraft Jar, not signed"() {
		setup:
		Path tempDir = Files.createTempDirectory("test")
		def tempJar = tempDir.resolve("client.jar")

		ZipUtils.add(tempJar, "hello.txt", "Hello World".bytes)

		def cert = CertificateChain.getRoot("mojangcs")
		when:
		JarVerifier.verify(tempJar, cert)
		then:
		def e = thrown SignatureVerificationFailure
		e.message == "Jar entry hello.txt does not have a signature"
	}

	def "not minecraft"() {
		setup:
		def installerJar = downloadJarIfNotExists(INSTALLER_JAR_URL, "installer.jar")

		def cert = CertificateChain.getRoot("mojangcs")
		when:
		JarVerifier.verify(installerJar, cert)
		then:
		def e = thrown SignatureVerificationFailure
		e.message == "Certificate mismatch: CN=Fabric,OU=CI,O=Fabric,L=Unknown,ST=Unknown,C=Unknown != OU=Class 3 Public Primary Certification Authority,O=VeriSign\\, Inc.,C=US"
	}

	static Path downloadJarIfNotExists(String url, String name) {
		File dst = new File(mcJarDir, name)

		if (!dst.exists()) {
			dst.parentFile.mkdirs()
			Download.create(url)
					.defaultCache()
					.downloadPath(dst.toPath())
		}

		return dst.toPath()
	}
}
