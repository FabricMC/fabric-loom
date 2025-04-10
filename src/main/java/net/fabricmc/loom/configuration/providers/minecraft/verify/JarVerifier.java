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
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.ZipReprocessorUtil;

public final class JarVerifier {
	private static final Logger LOGGER = LoggerFactory.getLogger(JarVerifier.class);

	private JarVerifier() {
	}

	public static void verify(Path jarPath, CertificateChain certificateChain) throws IOException, SignatureVerificationFailure {
		Objects.requireNonNull(jarPath, "jarPath");
		Objects.requireNonNull(certificateChain, "certificateChain");

		if (certificateChain.issuer() != null) {
			throw new IllegalStateException("Can only verify jars from a root certificate");
		}

		Set<X509Certificate> jarCertificates = new HashSet<>();

		try (JarFile jarFile = new JarFile(jarPath.toFile(), true)) {
			for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
				if (ZipReprocessorUtil.isSpecialFile(jarEntry.getName())
						|| jarEntry.getName().equals("META-INF/MANIFEST.MF")
						|| jarEntry.isDirectory()) {
					continue;
				}

				try {
					// Must read the entire entry to trigger the signature verification
					byte[] bytes = jarFile.getInputStream(jarEntry).readAllBytes();
				} catch (SecurityException e) {
					throw new SignatureVerificationFailure("Jar entry " + jarEntry.getName() + " failed signature verification", e);
				}

				Certificate[] entryCertificates = jarEntry.getCertificates();

				if (entryCertificates == null) {
					throw new SignatureVerificationFailure("Jar entry " + jarEntry.getName() + " does not have a signature");
				}

				Arrays.stream(entryCertificates)
						.map(c -> (X509Certificate) c)
						.forEach(jarCertificates::add);
			}
		}

		CertificateChain jarCertificateChain = CertificateChain.getRoot(jarCertificates);

		jarCertificateChain.verifyChainMatches(certificateChain);
		LOGGER.debug("Jar {} is signed by the expected certificate", jarPath);
	}
}
