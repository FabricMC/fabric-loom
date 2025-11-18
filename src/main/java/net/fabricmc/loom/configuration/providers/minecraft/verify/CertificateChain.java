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

package net.fabricmc.loom.configuration.providers.minecraft.verify;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A node in the certificate chain.
 */
public interface CertificateChain {
	/**
	 * The certificate itself.
	 */
	X509Certificate certificate();

	/**
	 * The issuer of this certificate, or null if this is a root certificate.
	 */
	@Nullable CertificateChain issuer();

	/**
	 * The children of this certificate, or an empty list if this is a leaf certificate.
	 */
	List<CertificateChain> children();

	/**
	 * Verify that this certificate chain matches exactly with another one.
	 * @param other the other certificate chain
	 */
	void verifyChainMatches(CertificateChain other) throws SignatureVerificationFailure;

	/**
	 * Recursively visit all certificates in the chain, including this one.
	 */
	static void visitAll(CertificateChain chain, CertificateConsumer consumer) throws SignatureVerificationFailure {
		consumer.accept(chain.certificate());

		for (CertificateChain child : chain.children()) {
			visitAll(child, consumer);
		}
	}

	/**
	 * Load certificate chain from the classpath, returning the root certificate.
	 */
	static CertificateChain getRoot(String name) throws IOException {
		try (InputStream is = JarVerifier.class.getClassLoader().getResourceAsStream("certs/" + name + ".cer")) {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			Collection<X509Certificate> certificates = cf.generateCertificates(is).stream()
					.map(c -> (X509Certificate) c)
					.toList();
			return getRoot(certificates);
		} catch (CertificateException e) {
			throw new RuntimeException("Failed to load certificate: " + name, e);
		}
	}

	/**
	 * Takes an unordered collection of certificates and builds a tree structure.
	 */
	static CertificateChain getRoot(Collection<X509Certificate> certificates) {
		Map<String, Impl> certificateNodes = new HashMap<>();

		for (X509Certificate certificate : certificates) {
			Impl node = new Impl();
			node.certificate = certificate;
			certificateNodes.put(certificate.getSubjectX500Principal().getName(), node);
		}

		for (X509Certificate certificate : certificates) {
			String subject = certificate.getSubjectX500Principal().getName();
			String issuer = certificate.getIssuerX500Principal().getName();

			if (subject.equals(issuer)) {
				continue; // self-signed
			}

			Impl parent = certificateNodes.get(issuer);
			Impl self = certificateNodes.get(subject);

			if (parent == self) {
				throw new IllegalStateException("Certificate " + subject + " is its own issuer");
			}

			if (parent == null) {
				throw new IllegalStateException("Certificate " + subject + " defines issuer " + issuer + " which is not in the chain");
			}

			parent.children.add(self);
			self.issuer = parent;
		}

		List<Impl> roots = certificateNodes.values()
				.stream()
				.filter(node -> node.issuer == null)
				.toList();

		if (roots.size() != 1) {
			throw new IllegalStateException("Expected exactly one root certificate, but found " + roots.size());
		}

		return roots.get(0);
	}

	@FunctionalInterface
	interface CertificateConsumer {
		void accept(X509Certificate certificate) throws SignatureVerificationFailure;
	}

	class Impl implements CertificateChain {
		X509Certificate certificate;
		CertificateChain.@Nullable Impl issuer;
		List<CertificateChain> children = new ArrayList<>();

		private Impl() {
		}

		@Override
		public X509Certificate certificate() {
			return certificate;
		}

		@Override
		public @Nullable CertificateChain issuer() {
			return issuer;
		}

		@Override
		public List<CertificateChain> children() {
			return children;
		}

		@Override
		public void verifyChainMatches(CertificateChain other) throws SignatureVerificationFailure {
			if (!this.certificate().equals(other.certificate())) {
				throw new SignatureVerificationFailure("Certificate mismatch: " + this + " != " + other);
			}

			if (this.children().size() != other.children().size()) {
				throw new SignatureVerificationFailure("Certificate mismatch: " + this + " has " + this.children().size() + " children, but " + other + " has " + other.children().size());
			}

			if (this.children.isEmpty()) {
				// Fine, leaf certificate
				return;
			}

			if (this.children.size() != 1) {
				// TODO support this, not needed currently
				throw new UnsupportedOperationException("Validating Certificate chain with multiple children is not supported");
			}

			this.children.get(0).verifyChainMatches(other.children().get(0));
		}

		@Override
		public String toString() {
			return certificate.getSubjectX500Principal().getName();
		}
	}
}
