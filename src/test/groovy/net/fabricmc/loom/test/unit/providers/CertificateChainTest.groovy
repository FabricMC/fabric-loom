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
import net.fabricmc.loom.configuration.providers.minecraft.verify.SignatureVerificationFailure
import net.fabricmc.loom.test.util.CertificateUtils

class CertificateChainTest extends Specification {
	def "load mojang's cert chain"() {
		when:
		def chain = CertificateChain.getRoot("mojangcs")

		then:
		chain.certificate().issuerX500Principal.name == "OU=Class 3 Public Primary Certification Authority,O=VeriSign\\, Inc.,C=US"
	}

	def "load certificate chain"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def intermediate = CertificateUtils.createCert(keyPair, "CN=Test Intermediate Certificate", root)
		def leaf = CertificateUtils.createCert(keyPair, "CN=Test Leaf Certificate", intermediate)

		when:
		def chain = CertificateChain.getRoot([root, intermediate, leaf])

		then:
		chain.issuer() == null
		chain.certificate() == root
		chain.children().size() == 1

		chain.children()[0].issuer().certificate() == root
		chain.children()[0].certificate() == intermediate
		chain.children()[0].children().size() == 1
	}

	def "matching cert chain"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def intermediate = CertificateUtils.createCert(keyPair, "CN=Test Intermediate Certificate", root)
		def leaf = CertificateUtils.createCert(keyPair, "CN=Test Leaf Certificate", intermediate)

		when:
		def chain1 = CertificateChain.getRoot([root, intermediate, leaf])
		def chain2 = CertificateChain.getRoot([root, intermediate, leaf])

		then:
		chain1.verifyChainMatches(chain2)
	}

	def "different leaf cert"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def intermediate = CertificateUtils.createCert(keyPair, "CN=Test Intermediate Certificate", root)
		def leaf1 = CertificateUtils.createCert(keyPair, "CN=Test Leaf 1 Certificate", intermediate)
		def leaf2 = CertificateUtils.createCert(keyPair, "CN=Test Leaf 2 Certificate", intermediate)

		when:
		def chain1 = CertificateChain.getRoot([root, intermediate, leaf1])
		def chain2 = CertificateChain.getRoot([root, intermediate, leaf2])

		chain1.verifyChainMatches(chain2)

		then:
		thrown SignatureVerificationFailure
	}

	def "different cert"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def root = CertificateUtils.createCert(keyPair, "CN=Test Root Certificate")
		def root2 = CertificateUtils.createCert(keyPair, "CN=Test Root 2 Certificate")

		when:
		def chain1 = CertificateChain.getRoot([root])
		def chain2 = CertificateChain.getRoot([root2])

		chain1.verifyChainMatches(chain2)

		then:
		thrown SignatureVerificationFailure
	}
}
