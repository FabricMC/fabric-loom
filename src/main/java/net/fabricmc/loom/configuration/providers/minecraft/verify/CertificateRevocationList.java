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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.download.DownloadException;

public record CertificateRevocationList(Collection<X509CRL> crls, boolean downloadFailure) {
	/**
	 * Hardcoded CRLs for Mojang's certificate, we don't want to add a large dependency just to parse this each time.
	 */
	public static final List<String> CSC3_2010 = List.of(
			"http://crl.verisign.com/pca3-g5.crl",
			"http://crl.verisign.com/pca3.crl",
			"http://csc3-2010-crl.verisign.com/CSC3-2010.crl"
	);

	private static final Logger LOGGER = LoggerFactory.getLogger(CertificateRevocationList.class);

	/**
	 * Attempt to download the CRL from the given URL, if we fail to get it its not the end of the world.
	 */
	public static CertificateRevocationList create(Project project, List<String> urls) throws IOException {
		List<X509CRL> crls = new ArrayList<>();

		boolean downloadFailure = false;

		for (String url : urls) {
			try {
				crls.add(download(project, url));
			} catch (DownloadException e) {
				LOGGER.info("Failed to download CRL from {}: {}", url, e.getMessage());
				LOGGER.info("Loom will not be able to verify the integrity of the minecraft jar signature");
				downloadFailure = true;
			}
		}

		return new CertificateRevocationList(crls, downloadFailure);
	}

	static X509CRL download(Project project, String url) throws IOException {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final String name = url.substring(url.lastIndexOf('/') + 1);
		final Path path = extension.getFiles().getUserCache().toPath()
				.resolve("crl")
				.resolve(name);

		LOGGER.info("Downloading CRL from {} to {}", url, path);

		extension.download(url)
				.allowInsecureProtocol()
				.maxAge(Duration.ofDays(7)) // Cache the CRL for a week
				.downloadPath(path);

		return parse(path);
	}

	static X509CRL parse(Path path) throws IOException {
		try (InputStream inStream = Files.newInputStream(path)) {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			return (X509CRL) cf.generateCRL(inStream);
		} catch (CRLException | CertificateException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Verify that none of the certs in the chain are revoked.
	 * @throws SignatureVerificationFailure if the certificate is revoked
	 */
	public void verify(CertificateChain certificateChain) throws SignatureVerificationFailure {
		CertificateChain.visitAll(certificateChain, this::verify);
	}

	private void verify(X509Certificate certificate) throws SignatureVerificationFailure {
		for (X509CRL crl : crls) {
			if (crl.isRevoked(certificate)) {
				throw new SignatureVerificationFailure("Certificate " + certificate.getSubjectX500Principal().getName() + " is revoked");
			}
		}
	}
}
