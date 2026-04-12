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

package net.fabricmc.loom.test.unit.layeredmappings

import net.fabricmc.loom.api.mappings.layered.spec.FileSpec
import net.fabricmc.loom.configuration.providers.mappings.file.FileMappingsSpecBuilderImpl
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata

class LayeredMappingsUnpickTest extends LayeredMappingsSpecification {
	def "compile unpick data from yarn layer with unpick metadata v1"() {
		setup:
		intermediaryUrl = INTERMEDIARY_1_17_URL
		mockMinecraftProvider.getVersionInfo() >> VERSION_META_1_17
		when:
		withMavenFile(YARN_1_17_NOTATION, downloadFile(YARN_1_17_URL, "yarn-1.17.jar"))
		def builder = FileMappingsSpecBuilderImpl.builder(FileSpec.create(YARN_1_17_NOTATION)).containsUnpick()
		def unpickData = getUnpickData(
				new IntermediaryMappingsSpec(),
				builder.build()
				)
		def metadata = unpickData.metadata()
		then:
		metadata instanceof UnpickMetadata.V2
		metadata.namespace() == "named"
		metadata.constants() == "${YARN_1_17_NOTATION}:constants"

		unpickData.definitions().length == 56119
	}

	def "compile unpick data from yarn layer with unpick metadata v2 without constants"() {
		setup:
		intermediaryUrl = INTERMEDIARY_1_21_11_URL
		mockMinecraftProvider.getVersionInfo() >> VERSION_META_1_21_11
		when:
		withMavenFile(YARN_1_21_11_NOTATION, downloadFile(YARN_1_21_11_URL, "yarn-1.21.11.jar"))
		def builder = FileMappingsSpecBuilderImpl.builder(FileSpec.create(YARN_1_21_11_NOTATION)).containsUnpick()
		def unpickData = getUnpickData(
				new IntermediaryMappingsSpec(),
				builder.build()
				)
		def metadata = unpickData.metadata()
		then:
		metadata instanceof UnpickMetadata.V2
		metadata.namespace() == "intermediary"
		metadata.constants() == null

		unpickData.definitions().length == 66489
	}
}
