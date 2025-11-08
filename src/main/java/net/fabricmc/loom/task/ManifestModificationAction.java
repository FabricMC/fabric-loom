/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipUtils;

/**
 * Action that modifies the manifest of a jar file to add Loom metadata.
 * Configuration-cache-compatible implementation using providers.
 */
public class ManifestModificationAction implements Action<Task>, Serializable {
	private final Provider<JarManifestService> manifestService;
	private final String targetNamespace;
	private final Provider<Boolean> areEnvironmentSourceSetsSplit;
	private final Provider<List<String>> clientOnlyEntries;

	public ManifestModificationAction(
			Provider<JarManifestService> manifestService,
			String targetNamespace,
			Provider<Boolean> areEnvironmentSourceSetsSplit,
			Provider<List<String>> clientOnlyEntries) {
		this.manifestService = manifestService;
		this.targetNamespace = targetNamespace;
		this.areEnvironmentSourceSetsSplit = areEnvironmentSourceSetsSplit;
		this.clientOnlyEntries = clientOnlyEntries;
	}

	@Override
	public void execute(@NotNull Task t) {
		final Jar jarTask = (Jar) t;
		final File jarFile = jarTask.getArchiveFile().get().getAsFile();

		try {
			modifyManifest(jarFile);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to modify jar manifest for " + jarFile.getName(), e);
		}
	}

	private void modifyManifest(File jarFile) throws IOException {
		Map<String, String> manifestAttributes = new HashMap<>();

		// Set the mapping namespace to "official" for non-remapped jars
		manifestAttributes.put(Constants.Manifest.MAPPING_NAMESPACE, targetNamespace);

		// Set split environment flag if source sets are split (even for common-only jars)
		if (areEnvironmentSourceSetsSplit.get()) {
			manifestAttributes.put(Constants.Manifest.SPLIT_ENV, "true");
		}

		// Add client-only entries list if present
		if (clientOnlyEntries != null && !clientOnlyEntries.get().isEmpty()) {
			manifestAttributes.put(Constants.Manifest.CLIENT_ENTRIES, String.join(";", clientOnlyEntries.get()));
		}

		int count = ZipUtils.transform(jarFile.toPath(), Map.of(Constants.Manifest.PATH, bytes -> {
			var manifest = new Manifest(new ByteArrayInputStream(bytes));

			// Apply standard Loom manifest attributes (Gradle version, Loom version, etc.)
			manifestService.get().apply(manifest, manifestAttributes);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			manifest.write(out);
			return out.toByteArray();
		}));

		Check.require(count > 0, "Did not transform any jar manifest");
	}
}
