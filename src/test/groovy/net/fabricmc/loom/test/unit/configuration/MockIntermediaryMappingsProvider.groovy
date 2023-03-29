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

package net.fabricmc.loom.test.unit.configuration

import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.download.DownloadBuilder
import org.gradle.api.provider.Property

import java.nio.file.Path
import java.util.function.Function

class MockIntermediaryMappingsProvider extends IntermediaryMappingsProvider {
	private static String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/1.19.4/intermediary-1.19.4-v2.jar"

	final DownloadBuilder downloadBuilder

	MockIntermediaryMappingsProvider(DownloadBuilder downloadBuilder) {
		this.downloadBuilder = downloadBuilder
	}

	Property<String> minecraftVersion = GradleTestUtil.mockProperty("1.19.4")
	Property<Boolean> refreshDeps = GradleTestUtil.mockProperty(false)
	Property<String> intermediaryUrl = GradleTestUtil.mockProperty(INTERMEDIARY_URL)
	Property<Function<String, DownloadBuilder>> downloader = GradleTestUtil.mockProperty(download())

	private Function<String, DownloadBuilder> download() {
		return {
			downloadBuilder
		}
	}

	static void writeIntermediaryJar(Path path, Map<String, String> metadata = [:]) {
		ZipUtils.add(path, "mappings/mappings.tiny", "tiny\t2\t0\tofficial\tintermediary")
		ZipUtils.add(path, "META-INF/MANIFEST.MF", ZipTestUtils.manifest(metadata))
	}
}

