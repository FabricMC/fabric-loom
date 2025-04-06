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

import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.minecraft.verify.CertificateChain
import net.fabricmc.loom.configuration.providers.minecraft.verify.CertificateRevocationList
import net.fabricmc.loom.configuration.providers.minecraft.verify.SignatureVerificationFailure
import net.fabricmc.loom.test.util.CertificateUtils
import net.fabricmc.loom.test.util.GradleTestUtil

class CertificateRevocationListTest extends Specification {
	// Test to make sure that the CRL URL is correct for the mojang cert chain
	// As we don't want to depend on bouncycastle in the main project just to extract the same crl url each time
	def "crl url matches"() {
		given:
		def cert = CertificateChain.getRoot("mojangcs")
		when:
		def crls = CertificateUtils.getCrls(cert)
		then:
		crls.sort() == CertificateRevocationList.CSC3_2010
	}

	def "valid cert"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def intermediate = CertificateUtils.createCert(keyPair, "CN=Test Intermediate Certificate", root)
		def validLeaf = CertificateUtils.createCert(keyPair, "CN=Test Valid Leaf Certificate", intermediate)
		def revokedLeaf = CertificateUtils.createCert(keyPair, "CN=Test Revoked Leaf Certificate", intermediate)

		def x509crl = CertificateUtils.createCrl(keyPair, intermediate, [revokedLeaf])

		def chain = CertificateChain.getRoot([root, intermediate, validLeaf])

		when:
		def crl = new CertificateRevocationList([x509crl], false)

		then:
		crl.verify(chain)
	}

	def "revoked cert"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def intermediate = CertificateUtils.createCert(keyPair, "CN=Test Intermediate Certificate", root)
		def revokedLeaf = CertificateUtils.createCert(keyPair, "CN=Test Revoked Leaf Certificate", intermediate)

		def x509crl = CertificateUtils.createCrl(keyPair, intermediate, [revokedLeaf])

		def chain = CertificateChain.getRoot([
			root,
			intermediate,
			revokedLeaf
		])

		when:
		def crl = new CertificateRevocationList([x509crl], false)
		crl.verify(chain)

		then:
		thrown SignatureVerificationFailure
	}

	def "Verify Mojang cert"() {
		given:
		def project = GradleTestUtil.mockProject()
		def cert = CertificateChain.getRoot("mojangcs")
		when:
		def crl = CertificateRevocationList.create(project, CertificateRevocationList.CSC3_2010)
		then:
		!crl.downloadFailure()
		crl.verify(cert)
	}

	def "Invalid URL"() {
		given:
		def project = GradleTestUtil.mockProject()
		def cert = CertificateChain.getRoot("mojangcs")
		when:
		def crl = CertificateRevocationList.create(project, ["http://invalid.url"])
		then:
		crl.downloadFailure()
		crl.verify(cert)
	}
}
