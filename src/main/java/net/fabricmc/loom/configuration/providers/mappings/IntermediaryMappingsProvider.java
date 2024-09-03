/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.inject.Inject;

import com.google.common.net.UrlEscapers;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.extension.LoomGradleExtensionApiImpl;
import net.fabricmc.loom.util.Checksum;

@ApiStatus.Internal
public abstract class IntermediaryMappingsProvider extends IntermediateMappingsProviderInternal {
	public static final String NAME = "intermediary-v2";
	private static final String FABRIC_INTERMEDIARY_GROUP_NAME = "net.fabricmc:intermediary";
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateMappingsProvider.class);

	public abstract Property<String> getIntermediaryUrl();

	public abstract Property<Boolean> getRefreshDeps();

	@Inject
	public abstract DependencyFactory getDependencyFactory();

	@Override
	public void provide(Path tinyMappings, @Nullable Project project) throws IOException {
		if (Files.exists(tinyMappings) && !getRefreshDeps().get()) {
			return;
		}

		// Download and extract intermediary
		final Path intermediaryJarPath = Files.createTempFile(getName(), ".jar");
		final String encodedMcVersion = UrlEscapers.urlFragmentEscaper().escape(getMinecraftVersion().get());
		final String urlRaw = getIntermediaryUrl().get();

		if (project != null && urlRaw.equals(LoomGradleExtensionApiImpl.DEFAULT_INTERMEDIARY_URL)) {
			final ModuleDependency intermediaryDep = getDependencyFactory()
					.create(FABRIC_INTERMEDIARY_GROUP_NAME + ':' + encodedMcVersion);
			intermediaryDep.artifact(new Action<DependencyArtifact>() {
				@Override
				public void execute(final DependencyArtifact dependencyArtifact) {
					dependencyArtifact.setClassifier("v2");
				}
			});
			final Configuration config = project.getConfigurations().detachedConfiguration(intermediaryDep);

			Files.copy(
					config.getSingleFile().toPath(),
					intermediaryJarPath,
					StandardCopyOption.REPLACE_EXISTING
			);
			Files.deleteIfExists(tinyMappings);
		} else {
			final String url = urlRaw.formatted(encodedMcVersion);

			LOGGER.info("Downloading intermediary from {}", url);

			Files.deleteIfExists(tinyMappings);
			Files.deleteIfExists(intermediaryJarPath);

			getDownloader().get().apply(url)
					.defaultCache()
					.downloadPath(intermediaryJarPath);
		}

		MappingConfiguration.extractMappings(intermediaryJarPath, tinyMappings);
	}

	@Override
	public @NotNull String getName() {
		final String encodedMcVersion = UrlEscapers.urlFragmentEscaper().escape(getMinecraftVersion().get());
		final String urlRaw = getIntermediaryUrl().get();

		if (!LoomGradleExtensionApiImpl.DEFAULT_INTERMEDIARY_URL.equals(urlRaw)) {
			final String url = getIntermediaryUrl().get().formatted(encodedMcVersion);

			return NAME + "-" + Checksum.sha1Hex(url.getBytes(StandardCharsets.UTF_8));
		}

		return NAME;
	}
}
