/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.util

import java.time.Duration

import com.google.gson.Gson
import com.google.gson.GsonBuilder

import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.download.Download

class MinecraftTestUtils {
	private static final File TEST_DIR = new File(LoomTestConstants.TEST_DIR, "minecraft")
	public static final Gson GSON = new GsonBuilder().create()

	static MinecraftVersionMeta getVersionMeta(String id) {
		def versionManifest = download(Constants.VERSION_MANIFESTS, "version_manifest.json")
		def manifest = GSON.fromJson(versionManifest, ManifestVersion.class)
		def version = manifest.versions().find { it.id == id }

		def metaJson = download(version.url, "${id}.json")
		GSON.fromJson(metaJson, MinecraftVersionMeta.class)
	}

	static String download(String url, String name) {
		Download.create(url)
				.maxAge(Duration.ofDays(31))
				.downloadString(new File(TEST_DIR, name).toPath())
	}
}
