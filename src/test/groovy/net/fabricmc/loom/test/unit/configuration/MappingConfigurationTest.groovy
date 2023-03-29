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

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.configuration.providers.mappings.IntermediateMappingsService
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider
import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.test.util.ZipTestUtils
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.download.DownloadBuilder
import net.fabricmc.loom.util.service.SharedServiceManager
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static org.mockito.Mockito.when

class MappingConfigurationTest extends Specification {
	def "generateMappingsJar"() {
		given:
			def tempDir = File.createTempDir()
			def mappingsConfig = new MappingConfiguration("test", tempDir.toPath())
			def project = GradleTestUtil.mockProject()
			def extension = LoomGradleExtension.get(project)
			def sharedServiceManager = Mock(SharedServiceManager)
			def download = Mock(DownloadBuilder)
			def intermediaryMappingsProvider = new MockIntermediaryMappingsProvider(download)
			download.defaultCache() >> download
			download.downloadPath(_) >> { Path path ->
				MockIntermediaryMappingsProvider.writeIntermediaryJar(path, manifest)
			}
			def minecraftProvider = Mock(MinecraftProvider)
			minecraftProvider.file(_) >> { String name ->
				return new File(tempDir, name)
			}
			def intermediateMappingsService = IntermediateMappingsService.create(intermediaryMappingsProvider, minecraftProvider)
			when(extension.getIntermediateMappingsProvider()).thenReturn(intermediaryMappingsProvider)
			sharedServiceManager.getOrCreateService(_, _) >> intermediateMappingsService
			Files.writeString(mappingsConfig.tinyMappings, "tiny\t2\t0\tofficial\tintermediary\tnamed")

		when:
			mappingsConfig.generateMappingsJar(project, sharedServiceManager, minecraftProvider)

		then:
			new String(ZipUtils.unpack(mappingsConfig.tinyMappingsJar, "mappings/mappings.tiny")) == "tiny\t2\t0\tofficial\tintermediary\tnamed"
			new String(ZipUtils.unpack(mappingsConfig.tinyMappingsJar, "META-INF/MANIFEST.MF")) == ZipTestUtils.manifest(manifest)

		where:
			manifest                                                    | _
			[:]      								                    | _ // Current shipping
			["Game-Id": "23w13a", "Game-Version": "1.20-alpha.23.13.a"] | _ // Newer intermediary
	}
}
