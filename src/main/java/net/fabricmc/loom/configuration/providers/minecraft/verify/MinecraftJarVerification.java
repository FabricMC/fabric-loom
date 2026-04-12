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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.Checksum;

public abstract class MinecraftJarVerification {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftJarVerification.class);

	private final String minecraftVersion;

	@Inject
	protected abstract Project getProject();

	@Inject
	public MinecraftJarVerification(String minecraftVersion) {
		this.minecraftVersion = minecraftVersion;
	}

	public void verifyClientJar(Path path) throws IOException, SignatureVerificationFailure {
		verifyJarSignature(path, KnownJarType.CLIENT);
	}

	public void verifyServerJar(Path path) throws IOException, SignatureVerificationFailure {
		verifyJarSignature(path, KnownJarType.SERVER);
	}

	private void verifyJarSignature(Path path, KnownJarType type) throws IOException, SignatureVerificationFailure {
		CertificateChain chain = CertificateChain.getRoot("mojangcs");
		CertificateRevocationList revocationList = CertificateRevocationList.create(getProject(), CertificateRevocationList.CSC3_2010);

		try {
			revocationList.verify(chain);
			JarVerifier.verify(path, chain);
		} catch (SignatureVerificationFailure e) {
			if (isValidKnownVersion(path, minecraftVersion, type)) {
				LOGGER.info("Minecraft {} signature verification failed, but is a known version", path.getFileName());
				return;
			}

			LOGGER.error("Verification of Minecraft {} signature failed: {}", path.getFileName(), e.getMessage());

			try {
				Files.delete(path);
			} catch (IOException ioe) {
				LOGGER.error("Failed to delete invalid Minecraft jar: {}", path, ioe);
			}

			throw e;
		}
	}

	private boolean isValidKnownVersion(Path path, String version, KnownJarType type) throws IOException, SignatureVerificationFailure {
		Map<String, String> knownVersions = type.getKnownVersions();
		String expectedHash = knownVersions.get(version);

		if (expectedHash == null) {
			return false;
		}

		LOGGER.info("Found executed hash ({}) for known version: {}", expectedHash, version);
		Checksum.Result hash = Checksum.of(path).sha256();

		if (hash.matchesStr(expectedHash)) {
			LOGGER.info("Minecraft {} hash matches known version", path.getFileName());
			return true;
		}

		throw new SignatureVerificationFailure("Hash mismatch for known Minecraft version " + version + ": expected " + expectedHash + ", got " + hash);
	}

	private enum KnownJarType {
		CLIENT(KnownVersions::client),
		SERVER(KnownVersions::server),;

		private final Function<KnownVersions, Map<String, String>> knownVersions;

		KnownJarType(Function<KnownVersions, Map<String, String>> knownVersions) {
			this.knownVersions = knownVersions;
		}

		private Map<String, String> getKnownVersions() {
			return knownVersions.apply(KnownVersions.INSTANCE.get());
		}
	}
}
