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

package net.fabricmc.loom.util;

import org.gradle.api.plugins.ExtensionAware;

public class MirrorUtil {
	public static String getLibrariesBase(ExtensionAware aware) {
		if (aware.getExtensions().getExtraProperties().has("loom_libraries_base")) {
			return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_libraries_base"));
		}

		return Constants.LIBRARIES_BASE;
	}

	public static String getResourcesBase(ExtensionAware aware) {
		if (aware.getExtensions().getExtraProperties().has("loom_resources_base")) {
			return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_resources_base"));
		}

		return Constants.RESOURCES_BASE;
	}

	public static String getVersionManifests(ExtensionAware aware) {
		if (aware.getExtensions().getExtraProperties().has("loom_version_manifests")) {
			return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_version_manifests"));
		}

		return Constants.VERSION_MANIFESTS;
	}

	public static String getExperimentalVersions(ExtensionAware aware) {
		if (aware.getExtensions().getExtraProperties().has("loom_experimental_versions")) {
			return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_experimental_versions"));
		}

		return Constants.EXPERIMENTAL_VERSIONS;
	}

	public static String getFabricRepository(ExtensionAware aware) {
		if (aware.getExtensions().getExtraProperties().has("loom_fabric_repository")) {
			return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_fabric_repository"));
		}

		return Constants.FABRIC_REPOSITORY;
	}
}
