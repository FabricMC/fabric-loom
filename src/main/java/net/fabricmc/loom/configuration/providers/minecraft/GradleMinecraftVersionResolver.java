/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestLocations.ManifestLocation;
import net.fabricmc.loom.configuration.providers.minecraft.VersionsManifest.Version;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.download.DownloadBuilder;

final class GradleMinecraftVersionResolver {
	private static final String GROUP = "net.minecraft";
	private static final String MODULE = "minecraft";
	private static final DateTimeFormatter MAVEN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Project project;
	private final LoomGradleExtension extension;
	private final Function<String, DownloadBuilder> download;

	private GradleMinecraftVersionResolver(Project project, LoomGradleExtension extension, Function<String, DownloadBuilder> download) {
		this.project = project;
		this.extension = extension;
		this.download = download;
	}

	static boolean isGradleResolvedMinecraft(Dependency dependency) {
		return GROUP.equals(dependency.getGroup()) && MODULE.equals(dependency.getName());
	}

	static String resolve(Project project, Dependency dependency, Function<String, DownloadBuilder> download) {
		return new GradleMinecraftVersionResolver(project, LoomGradleExtension.get(project), download).resolve(dependency);
	}

	private String resolve(Dependency dependency) {
		if (extension.getCustomMinecraftMetadata().isPresent()) {
			return resolveCustomMetadataDependency(dependency);
		}

		try (MinecraftVersionNormalizer normalizer = FabricLoaderMinecraftVersionNormalizer.create(project)) {
			Metadata metadata = createMetadata(normalizer, false);
			Optional<String> directVersion = getDirectVersion(dependency);

			if (directVersion.isPresent() && metadata.isDirectUnnormalizedVersion(directVersion.get())) {
				// Exact manifest ids still need to work for versions that Loader cannot normalize.
				return directVersion.get();
			}

			validateRangeSelectors(dependency, metadata);
			publish(metadata);

			try {
				return resolveFromGradle(metadata);
			} catch (RuntimeException e) {
				Metadata refreshedMetadata = createMetadata(normalizer, true);

				if (directVersion.isPresent() && refreshedMetadata.isDirectUnnormalizedVersion(directVersion.get())) {
					// Keep the same direct-id escape hatch after refreshing stale manifest data.
					return directVersion.get();
				}

				validateRangeSelectors(dependency, refreshedMetadata);
				publish(refreshedMetadata);
				return resolveFromGradle(refreshedMetadata);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to close Minecraft version normalizer", e);
		}
	}

	private String resolveCustomMetadataDependency(Dependency dependency) {
		final Optional<String> directVersion = getDirectVersion(dependency);

		if (directVersion.isPresent()) {
			return directVersion.get();
		}

		throw new IllegalArgumentException("Cannot use dynamic or ranged net.minecraft:minecraft versions with custom Minecraft metadata");
	}

	private Metadata createMetadata(MinecraftVersionNormalizer normalizer, boolean forceDownload) {
		final Map<String, String> normalizedToRaw = new LinkedHashMap<>();
		final Set<String> unnormalized = new LinkedHashSet<>();
		String latest = null;
		String release = null;

		for (ManifestLocation location : extension.getVersionsManifests()) {
			final VersionsManifest manifest = readManifest(location, forceDownload);

			for (Version version : manifest.versions()) {
				final Optional<String> normalized = normalizer.normalize(version.id());

				if (normalized.isPresent()) {
					final String normalizedVersion = normalized.get();
					// Publish only normalized coordinates so Gradle's version ordering never sees raw
					// snapshot ids such as 24w46a.
					normalizedToRaw.putIfAbsent(normalizedVersion, version.id());
					latest = max(normalizer, latest, normalizedVersion);

					if (!normalizedVersion.contains("-")) {
						release = max(normalizer, release, normalizedVersion);
					}
				} else {
					unnormalized.add(version.id());
				}
			}
		}

		return new Metadata(normalizedToRaw, unnormalized, latest, release);
	}

	private static String max(MinecraftVersionNormalizer normalizer, @Nullable String a, String b) {
		if (a == null) {
			return b;
		}

		return normalizer.compare(a, b) < 0 ? b : a;
	}

	private VersionsManifest readManifest(ManifestLocation location, boolean forceDownload) {
		try {
			DownloadBuilder builder = download.apply(location.url());

			if (forceDownload) {
				builder = builder.forceDownload();
			} else {
				builder = builder.defaultCache();
			}

			final Path cacheFile = location.cacheFile(extension.getFiles().getUserCache().toPath());
			final String versionManifest = builder.downloadString(cacheFile);
			return LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read Minecraft versions manifest: " + location.url(), e);
		}
	}

	private void publish(Metadata metadata) {
		try {
			// Mojang does not publish net.minecraft:minecraft metadata. Loom publishes a tiny
			// local module so Gradle can do selector/range resolution against normalized ids.
			final Path moduleDir = extension.getFiles().getGlobalMinecraftRepo().toPath()
					.resolve(GROUP.replace('.', '/'))
					.resolve(MODULE);
			Files.createDirectories(moduleDir);

			final List<String> versions = new ArrayList<>(metadata.normalizedToRaw().keySet());

			for (String version : versions) {
				writePom(moduleDir, version);
			}

			writeMavenMetadata(moduleDir.resolve("maven-metadata.xml"), versions, metadata);
		} catch (IOException | ParserConfigurationException | TransformerException e) {
			throw new RuntimeException("Failed to publish Minecraft version metadata", e);
		}
	}

	private void writePom(Path moduleDir, String version) throws IOException, ParserConfigurationException, TransformerException {
		final Path versionDir = moduleDir.resolve(version);
		Files.createDirectories(versionDir);
		final Path pom = versionDir.resolve("%s-%s.pom".formatted(MODULE, version));

		final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element project = document.createElement("project");
		project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
		project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		project.setAttribute("xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd");
		document.appendChild(project);

		appendText(document, project, "modelVersion", "4.0.0");
		appendText(document, project, "groupId", GROUP);
		appendText(document, project, "artifactId", MODULE);
		appendText(document, project, "version", version);

		writeXml(pom, document);
	}

	private void writeMavenMetadata(Path metadataFile, List<String> versions, Metadata metadata) throws IOException, ParserConfigurationException, TransformerException {
		final String latest = metadata.latest() == null ? "" : metadata.latest();
		final String release = metadata.release() == null ? latest : metadata.release();

		final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element root = document.createElement("metadata");
		document.appendChild(root);

		appendText(document, root, "groupId", GROUP);
		appendText(document, root, "artifactId", MODULE);

		final Element versioning = document.createElement("versioning");
		root.appendChild(versioning);
		appendText(document, versioning, "latest", latest);
		appendText(document, versioning, "release", release);

		final Element versionsElement = document.createElement("versions");
		versioning.appendChild(versionsElement);

		for (String version : versions) {
			appendText(document, versionsElement, "version", version);
		}

		appendText(document, versioning, "lastUpdated", LocalDateTime.now(ZoneOffset.UTC).format(MAVEN_TIMESTAMP));

		writeXml(metadataFile, document);
	}

	private static Element appendText(Document document, Element parent, String name, String text) {
		final Element element = document.createElement(name);
		element.setTextContent(text);
		parent.appendChild(element);
		return element;
	}

	private static void writeXml(Path path, Document document) throws IOException, TransformerException {
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");

		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			transformer.transform(new DOMSource(document), new StreamResult(writer));
		}
	}

	private String resolveFromGradle(Metadata metadata) {
		// Resolve a copy so reading metadata here does not mutate the user's minecraft configuration.
		final Configuration configuration = project.getConfigurations()
				.getByName(Constants.Configurations.MINECRAFT_VERSION_RESOLVE)
				.copyRecursive();
		configuration.setCanBeResolved(true);
		configuration.setCanBeConsumed(false);

		for (ResolvedComponentResult component : configuration.getIncoming().getResolutionResult().getAllComponents()) {
			final ComponentIdentifier id = component.getId();

			if (id instanceof ModuleComponentIdentifier module
					&& GROUP.equals(module.getGroup())
					&& MODULE.equals(module.getModule())) {
				final String rawVersion = metadata.normalizedToRaw().get(module.getVersion());

				if (rawVersion != null) {
					return rawVersion;
				}

				throw new IllegalStateException("Resolved Minecraft version is not known to Loom: " + module.getVersion());
			}
		}

		throw new IllegalStateException("Gradle did not resolve a net.minecraft:minecraft version");
	}

	private static Optional<String> getDirectVersion(Dependency dependency) {
		if (dependency instanceof ExternalModuleDependency externalModuleDependency) {
			final VersionConstraint versionConstraint = externalModuleDependency.getVersionConstraint();

			if (isDirectVersion(versionConstraint.getStrictVersion())) {
				return Optional.of(versionConstraint.getStrictVersion());
			}

			if (isDirectVersion(versionConstraint.getRequiredVersion())) {
				return Optional.of(versionConstraint.getRequiredVersion());
			}
		}

		if (isDirectVersion(dependency.getVersion())) {
			return Optional.of(dependency.getVersion());
		}

		return Optional.empty();
	}

	private static void validateRangeSelectors(Dependency dependency, Metadata metadata) {
		for (String selector : getVersionSelectors(dependency)) {
			if (!isVersionRange(selector)) {
				continue;
			}

			for (String endpoint : getRangeEndpoints(selector)) {
				if (metadata.unnormalized().contains(endpoint)) {
					// Ranges over unknown ordering are ambiguous; direct exact ids are the supported
					// compatibility path for unnormalizable versions.
					throw new IllegalArgumentException("Cannot use unnormalizable Minecraft version in a range: " + endpoint);
				}
			}
		}
	}

	private static List<String> getVersionSelectors(Dependency dependency) {
		final List<String> selectors = new ArrayList<>();

		if (dependency.getVersion() != null) {
			selectors.add(dependency.getVersion());
		}

		if (dependency instanceof ExternalModuleDependency externalModuleDependency) {
			final VersionConstraint versionConstraint = externalModuleDependency.getVersionConstraint();
			addIfNotEmpty(selectors, versionConstraint.getStrictVersion());
			addIfNotEmpty(selectors, versionConstraint.getRequiredVersion());
			addIfNotEmpty(selectors, versionConstraint.getPreferredVersion());
			selectors.addAll(versionConstraint.getRejectedVersions());
		}

		return selectors;
	}

	private static List<String> getRangeEndpoints(String range) {
		final int comma = range.indexOf(',');

		if (comma < 0 || range.length() < 2) {
			return List.of(range);
		}

		final String lower = range.substring(1, comma).trim();
		final String upper = range.substring(comma + 1, range.length() - 1).trim();
		final List<String> endpoints = new ArrayList<>();
		addIfNotEmpty(endpoints, lower);
		addIfNotEmpty(endpoints, upper);
		return endpoints;
	}

	private static void addIfNotEmpty(List<String> values, @Nullable String value) {
		if (value != null && !value.isEmpty()) {
			values.add(value);
		}
	}

	private static boolean isDirectVersion(@Nullable String version) {
		return version != null && !version.isEmpty() && !isVersionRange(version) && !isDynamicVersion(version);
	}

	private static boolean isVersionRange(String version) {
		return version.startsWith("[") || version.startsWith("(");
	}

	private static boolean isDynamicVersion(String version) {
		return version.endsWith("+") || version.startsWith("latest.");
	}

	private record Metadata(
			Map<String, String> normalizedToRaw,
			Set<String> unnormalized,
			@Nullable String latest,
			@Nullable String release
	) {
		boolean isDirectUnnormalizedVersion(String version) {
			return unnormalized.contains(version);
		}
	}
}
