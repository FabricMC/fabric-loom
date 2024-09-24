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

package net.fabricmc.loom.test.unit

import java.nio.file.Path
import java.util.function.Function

import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider
import net.fabricmc.loom.configuration.providers.mappings.IntermediateMappingsService
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.download.Download

import static org.mockito.Mockito.spy
import static org.mockito.Mockito.when

class LoomMocks {
	static IntermediaryMappingsProvider intermediaryMappingsProviderMock(String minecraftVersion, String intermediaryUrl) {
		def minecraftVersionProperty = GradleTestUtil.mockProperty(minecraftVersion)
		def intermediaryUrlProperty = GradleTestUtil.mockProperty(intermediaryUrl)
		def downloaderProperty = GradleTestUtil.mockProperty(Download.&create as Function)
		def refreshDeps = GradleTestUtil.mockProperty(false)

		Objects.requireNonNull(minecraftVersionProperty.get())

		def mock = spy(IntermediaryMappingsProvider.class)
		when(mock.getMinecraftVersion()).thenReturn(minecraftVersionProperty)
		when(mock.getIntermediaryUrl()).thenReturn(intermediaryUrlProperty)
		when(mock.getDownloader()).thenReturn(downloaderProperty)
		when(mock.getRefreshDeps()).thenReturn(refreshDeps)
		return mock
	}

	static IntermediateMappingsService.Options intermediateMappingsServiceOptionsMock(Path intermediaryTiny, String expectedSrcNs) {
		def intermediaryTinyProperty = GradleTestUtil.mockProperty(intermediaryTiny)
		def expectedSrcNsProperty = GradleTestUtil.mockProperty(expectedSrcNs)

		def mock = spy(IntermediateMappingsService.Options.class)
		when(mock.getIntermediaryTiny()).thenReturn(intermediaryTinyProperty)
		when(mock.getExpectedSrcNs()).thenReturn(expectedSrcNsProperty)
		return mock
	}

	static IntermediateMappingsService intermediateMappingsServiceMock(IntermediateMappingsService.Options options) {
		def mock = spy(IntermediateMappingsService.class)
		when(mock.getOptions()).thenReturn(options)
		return mock
	}
}
