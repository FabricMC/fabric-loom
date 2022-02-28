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

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec
import net.fabricmc.loom.configuration.providers.mappings.file.FileMappingsSpecBuilderImpl
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingsSpec
import net.fabricmc.loom.util.DownloadUtil
import net.fabricmc.loom.util.ZipUtils
import spock.lang.Unroll

import java.nio.file.Path
import java.util.function.Consumer

class FileMappingLayerTest extends LayeredMappingsSpecification {
	@Unroll
	def "read Yarn mappings from #setupType.displayName"() {
		setup:
			intermediaryUrl = INTERMEDIARY_1_17_URL
			mockMinecraftProvider.getVersionInfo() >> VERSION_META_1_17
			setupType.setup.delegate = this
			def mappingFile = setupType.setup.call()
		when:
			def builder = FileMappingsSpecBuilderImpl.builder(FileSpec.create(mappingFile))
			setupType.mappingsSpec.accept(builder)
			def mappings = getLayeredMappings(
					new IntermediaryMappingsSpec(),
					builder.build()
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
		where:
			setupType << YarnSetupType.values()
	}

	// Also tests the custom fallback namespace and source namespace functionality
	def "read Mojang mappings from proguard"() {
		setup:
			intermediaryUrl = INTERMEDIARY_1_17_URL
			mockMinecraftProvider.getVersionInfo() >> VERSION_META_1_17
			def mappingsDownload = VERSION_META_1_17.download('client_mappings')
			def mappingsFile = new File(tempDir, 'mappings.txt')
			DownloadUtil.downloadIfChanged(new URL(mappingsDownload.url()), mappingsFile, mappingContext.logger)
		when:
			def mappings = getLayeredMappings(
					new IntermediaryMappingsSpec(),
					FileMappingsSpecBuilderImpl.builder(FileSpec.create(mappingsFile))
							.fallbackNamespaces('named', 'official')
							.mergeNamespace(MappingsNamespace.OFFICIAL)
							.build()
			)
			def tiny = getTiny(mappings)
		then:
			mappings.srcNamespace == "named"
			mappings.dstNamespaces == ["intermediary", "official"]
			mappings.classes.size() == 6113
			mappings.classes[0].srcName.hashCode() == 1869546970 // MojMap name, just check the hash
			mappings.classes[0].getDstName(0) == "net/minecraft/class_2354"
			mappings.classes[0].methods[0].args.size() == 0 // No Args
			tiny.contains('this$0')
	}

	enum YarnSetupType {
		TINY_JAR('tiny jar', { downloadFile(YARN_1_17_URL, "yarn.jar") }, { }),
		BARE_TINY('bare tiny file', {
			def yarnJar = downloadFile(YARN_1_17_URL, "yarn.jar")
			def yarnTiny = new File(tempDir, "yarn.tiny")
			yarnTiny.bytes = ZipUtils.unpack(yarnJar.toPath(), "mappings/mappings.tiny")
			yarnTiny
		}, { }),
		ENIGMA_ZIP('enigma zip', {
			// Recent Yarn data is not published as Enigma zips, so this zip is just a copy
			// of Yarn's repo at a60a3189
			Path.of("src/test/resources/mappings/yarn-1.17.zip")
		}, {
			it.mappingPath("mappings").enigmaMappings()
		}),
		YARN_V2_URL('yarn url', {
			YARN_1_17_URL
		}, { })
		;

		final String displayName
		final Closure<?> setup
		final Consumer<FileMappingsSpecBuilderImpl> mappingsSpec

		YarnSetupType(String displayName, Closure<?> setup, Consumer<FileMappingsSpecBuilderImpl> mappingsSpec) {
			this.displayName = displayName
			this.setup = setup
			this.mappingsSpec = mappingsSpec
		}
	}
}
