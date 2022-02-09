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

import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider
import net.fabricmc.loom.test.util.GradleTestUtil

import static org.mockito.Mockito.spy
import static org.mockito.Mockito.when

class LoomMocks {
    static IntermediaryMappingsProvider intermediaryMappingsProviderMock(String minecraftVersion, String intermediaryUrl) {
        def minecraftVersionProperty = GradleTestUtil.mockProperty(minecraftVersion)
        def intermediaryUrlProperty = GradleTestUtil.mockProperty(intermediaryUrl)

        Objects.requireNonNull(minecraftVersionProperty.get())

        def mock = spy(IntermediaryMappingsProvider.class)
        when(mock.getMinecraftVersion()).thenReturn(minecraftVersionProperty)
        when(mock.getIntermediaryUrl()).thenReturn(intermediaryUrlProperty)
        return mock
    }
}
