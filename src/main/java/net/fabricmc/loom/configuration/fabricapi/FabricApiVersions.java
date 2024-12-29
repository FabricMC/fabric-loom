/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.fabricapi;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.download.DownloadException;

public abstract class FabricApiVersions {
	@Inject
	protected abstract Project getProject();

	private final HashMap<String, Map<String, String>> moduleVersionCache = new HashMap<>();
	private final HashMap<String, Map<String, String>> deprecatedModuleVersionCache = new HashMap<>();

	public Dependency module(String moduleName, String fabricApiVersion) {
		return getProject().getDependencies()
				.create(getDependencyNotation(moduleName, fabricApiVersion));
	}

	public String moduleVersion(String moduleName, String fabricApiVersion) {
		String moduleVersion = moduleVersionCache
				.computeIfAbsent(fabricApiVersion, this::getApiModuleVersions)
				.get(moduleName);

		if (moduleVersion == null) {
			moduleVersion = deprecatedModuleVersionCache
					.computeIfAbsent(fabricApiVersion, this::getDeprecatedApiModuleVersions)
					.get(moduleName);
		}

		if (moduleVersion == null) {
			throw new RuntimeException("Failed to find module version for module: " + moduleName);
		}

		return moduleVersion;
	}

	private String getDependencyNotation(String moduleName, String fabricApiVersion) {
		return String.format("net.fabricmc.fabric-api:%s:%s", moduleName, moduleVersion(moduleName, fabricApiVersion));
	}

	private Map<String, String> getApiModuleVersions(String fabricApiVersion) {
		try {
			return populateModuleVersionMap(getApiMavenPom(fabricApiVersion));
		} catch (PomNotFoundException e) {
			throw new RuntimeException("Could not find fabric-api version: " + fabricApiVersion);
		}
	}

	private Map<String, String> getDeprecatedApiModuleVersions(String fabricApiVersion) {
		try {
			return populateModuleVersionMap(getDeprecatedApiMavenPom(fabricApiVersion));
		} catch (PomNotFoundException e) {
			// Not all fabric-api versions have deprecated modules, return an empty map to cache this fact.
			return Collections.emptyMap();
		}
	}

	private Map<String, String> populateModuleVersionMap(File pomFile) {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document pom = docBuilder.parse(pomFile);

			Map<String, String> versionMap = new HashMap<>();

			NodeList dependencies = ((Element) pom.getElementsByTagName("dependencies").item(0)).getElementsByTagName("dependency");

			for (int i = 0; i < dependencies.getLength(); i++) {
				Element dep = (Element) dependencies.item(i);
				Element artifact = (Element) dep.getElementsByTagName("artifactId").item(0);
				Element version = (Element) dep.getElementsByTagName("version").item(0);

				if (artifact == null || version == null) {
					throw new RuntimeException("Failed to find artifact or version");
				}

				versionMap.put(artifact.getTextContent(), version.getTextContent());
			}

			return versionMap;
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse " + pomFile.getName(), e);
		}
	}

	private File getApiMavenPom(String fabricApiVersion) throws PomNotFoundException {
		return getPom("fabric-api", fabricApiVersion);
	}

	private File getDeprecatedApiMavenPom(String fabricApiVersion) throws PomNotFoundException {
		return getPom("fabric-api-deprecated", fabricApiVersion);
	}

	private File getPom(String name, String version) throws PomNotFoundException {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final var mavenPom = new File(extension.getFiles().getUserCache(), "fabric-api/%s-%s.pom".formatted(name, version));

		try {
			extension.download(String.format("https://maven.fabricmc.net/net/fabricmc/fabric-api/%2$s/%1$s/%2$s-%1$s.pom", version, name))
					.defaultCache()
					.downloadPath(mavenPom.toPath());
		} catch (DownloadException e) {
			if (e.getStatusCode() == 404) {
				throw new PomNotFoundException(e);
			}

			throw new UncheckedIOException("Failed to download maven info to " + mavenPom.getName(), e);
		}

		return mavenPom;
	}

	private static class PomNotFoundException extends Exception {
		PomNotFoundException(Throwable cause) {
			super(cause);
		}
	}
}
