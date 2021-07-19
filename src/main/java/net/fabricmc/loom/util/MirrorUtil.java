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

public class MirrorUtil {
	private static String librariesBaseMirrors;
	private static String resourcesBaseMirrors;
	private static String versionManifests;
	private static String experimentalVersions;

	public static void setupMirror(String lib, String res, String manifest, String exp) {
		librariesBaseMirrors = lib;
		resourcesBaseMirrors = res;
		versionManifests = manifest;
		experimentalVersions = exp;
	}

	public static String getLibrariesBase() {
		return librariesBaseMirrors.equals("null") ? Constants.LIBRARIES_BASE : librariesBaseMirrors;
	}

	public static String getResourcesBase() {
		return resourcesBaseMirrors.equals("null") ? Constants.RESOURCES_BASE : resourcesBaseMirrors;
	}

	public static String getVersionManifests() {
		return versionManifests.equals("null") ? Constants.VERSION_MANIFESTS : versionManifests;
	}

	public static String getExperimentalVersions() {
		return experimentalVersions.equals("null") ? Constants.EXPERIMENTAL_VERSIONS : experimentalVersions;
	}

	public static String mirroredUrl(String path, MirrorSiteType type) {
		String url;
		switch (type) {
			case LIBRARY -> url = MirrorUtil.getLibrariesBase() + path;
			case RESOURCE -> url = MirrorUtil.getResourcesBase() + path;
			default -> url = path;
		}
		return url;
	}

	public enum MirrorSiteType {
		LIBRARY, RESOURCE
	}
}
