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

package net.fabricmc.loom.test.unit.layeredmappings

import net.fabricmc.loom.api.mappings.layered.spec.FileSpec
import net.fabricmc.loom.configuration.providers.mappings.file.FileMappingsSpecBuilderImpl
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingsSpec

class FileMappingLayerTest extends LayeredMappingsSpecification {
	def "read Yarn tiny mappings"() {
		setup:
			intermediaryUrl = INTERMEDIARY_1_17_URL
			mockMinecraftProvider.getVersionInfo() >> VERSION_META_1_17
		when:
			def yarnJar = downloadFile(YARN_1_17_URL, "yarn.jar")
			def mappings = getLayeredMappings(
					new IntermediaryMappingsSpec(),
					FileMappingsSpecBuilderImpl.builder(FileSpec.create(yarnJar)).build()
			)
		then:
			mappings.srcNamespace == "named"
			mappings.dstNamespaces == ["intermediary", "official"]
			mappings.classes.size() == 6111
			mappings.classes[0].srcName == "net/minecraft/block/FenceBlock"
			mappings.classes[0].getDstName(0) == "net/minecraft/class_2354"
			mappings.classes[0].fields[0].srcName == "cullingShapes"
			mappings.classes[0].fields[0].getDstName(0) == "field_11066"
			mappings.classes[0].methods[0].srcName == "canConnectToFence"
			mappings.classes[0].methods[0].getDstName(0) == "method_26375"
			mappings.classes[0].methods[0].args[0].srcName == "state"
	}
}
