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

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509CRL
import java.security.cert.X509Certificate

import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.CRLNumber
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import net.fabricmc.loom.configuration.providers.minecraft.verify.CertificateChain

/**
 * Test code, not for production use.
 */
class CertificateUtils {
	private static final JcaContentSignerBuilder SIGNER_BUILDER = new JcaContentSignerBuilder("SHA384withECDSA")
	private static final JcaX509CertificateConverter CERT_CONVERTER = new JcaX509CertificateConverter()
	private static final JcaX509CRLConverter CRL_CONVERTER = new JcaX509CRLConverter()

	static KeyPair generateKeyPair() {
		def keyPairGenerator = KeyPairGenerator.getInstance("EC")
		keyPairGenerator.initialize(384)
		return keyPairGenerator.generateKeyPair()
	}

	static X509Certificate createCert(KeyPair keyPair, String name, X509Certificate parent = null) {
		def issuerName = new X500Name(parent ? parent.subjectX500Principal.name : name)
		def subjectName = new X500Name(name)
		def notBefore = new Date()
		def notAfter = new Date(notBefore.getTime() + 365 * 24 * 60 * 60 * 1000) // 1 year

		def serialNumber = BigInteger.valueOf(System.currentTimeMillis())
		def builder = new JcaX509v3CertificateBuilder(
				issuerName,
				serialNumber,
				notBefore,
				notAfter,
				subjectName,
				keyPair.getPublic()
				)

		def contentSigner = SIGNER_BUILDER.build(keyPair.getPrivate())
		return CERT_CONVERTER.getCertificate(builder.build(contentSigner))
	}

	static X509CRL createCrl(KeyPair keyPair, X509Certificate issuerCert, List<X509Certificate> revokedCerts) {
		def builder = new JcaX509v2CRLBuilder(issuerCert, new Date())

		for (final def revoked in revokedCerts) {
			assert revoked.getIssuerX500Principal() == issuerCert.getSubjectX500Principal()
			builder.addCRLEntry(revoked.getSerialNumber(), new Date(), CRLReason.keyCompromise)
		}

		builder.addExtension(Extension.authorityKeyIdentifier, false,
				new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(keyPair.getPublic()))
		builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.ONE))

		def crlSigner = SIGNER_BUILDER.build(keyPair.getPrivate())
		return CRL_CONVERTER.getCRL(builder.build(crlSigner))
	}

	static List<String> getCrls(CertificateChain certificateChain) {
		def crls = [] as Set
		getCrls(certificateChain, crls)
		return crls.toList()
	}

	static void getCrls(CertificateChain certificateChain, Set<String> crls) {
		crls.addAll(getCrls(certificateChain.certificate()))

		certificateChain.children().each { child ->
			getCrls(child, crls)
		}
	}

	static ArrayList<String> getCrls(X509Certificate certificate) {
		byte[] crlDistributionPointsValue = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId())

		if (crlDistributionPointsValue == null) {
			return []
		}

		return CRLDistPoint
				.getInstance(JcaX509ExtensionUtils.parseExtensionValue(crlDistributionPointsValue))
				.getDistributionPoints()
				.findAll { it.getDistributionPoint().type == DistributionPointName.FULL_NAME }
				.collectMany { distPoint ->
					GeneralNames.getInstance(distPoint.getDistributionPoint().getName()).getNames()
							.findAll { it.tagNo == GeneralName.uniformResourceIdentifier }
							.collect { DERIA5String.getInstance(it.name).getString() }
				}
	}
}
