/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.test.unit.fmj

import com.google.gson.JsonSyntaxException
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.loom.util.fmj.FabricModJsonSource
import net.fabricmc.loom.util.fmj.mixin.MixinConfiguration
import net.fabricmc.loom.util.fmj.mixin.MixinRefmap

class MixinConfigurationTest extends Specification {
	def "read refmap from mod"() {
		given:
		def modSource = Mock(FabricModJsonSource)
		def mod = Mock(FabricModJson.Mockable)
		mod.getSource() >> modSource
		mod.getMixinConfigurations() >> ["test_config.json"]
		modSource.read("test_config.json") >> '{"refmap": "test_refmap.json"}'.bytes
		modSource.read("test_refmap.json") >> REFMAP.bytes

		when:
		def configs = MixinConfiguration.fromMod(mod)
		def config = configs[0]
		def refmap = config.refmap()

		then:
		configs.size() == 1
		refmap != null
		refmap.refmapPath() == "test_refmap.json"
		refmap.getData(NAMESPACE) != null

		and: "test MixinMappingData.remap"
		def mappingData = refmap.getData(NAMESPACE)
		mappingData.remap("net/fabricmc/fabric/mixin/block/ChunkSectionBlockStateCounterMixin", "Lnet/minecraft/block/BlockState;isAir()Z") == "Lnet/minecraft/class_2680;method_26215()Z"
		mappingData.remap("net/fabricmc/fabric/mixin/block/ChunkSectionBlockStateCounterMixin", "unknown") == "unknown"
		mappingData.remap("unknown", "unknown") == "unknown"
	}

	def "read refmap from mod with no refmap"() {
		given:
		def modSource = Mock(FabricModJsonSource)
		def mod = Mock(FabricModJson.Mockable)
		mod.getSource() >> modSource
		mod.getMixinConfigurations() >> ["test_config.json"]
		modSource.read("test_config.json") >> '{}'.bytes

		when:
		def configs = MixinConfiguration.fromMod(mod)

		then:
		configs.size() == 1
		configs[0].refmap() == null
	}

	def "read refmap from mod with invalid JSON"() {
		given:
		def modSource = Mock(FabricModJsonSource)
		def mod = Mock(FabricModJson.Mockable)
		mod.getSource() >> modSource
		mod.getMixinConfigurations() >> ["test_config.json"]
		modSource.read("test_config.json") >> 'invalid json'.bytes

		when:
		MixinConfiguration.fromMod(mod)

		then:
		thrown(JsonSyntaxException)
	}

	def "read refmap from mod with multiple configurations"() {
		given:
		def modSource = Mock(FabricModJsonSource)
		def mod = Mock(FabricModJson.Mockable)
		mod.getSource() >> modSource
		mod.getMixinConfigurations() >> [
			"config1.json",
			"config2.json"
		]
		modSource.read("config1.json") >> '{"refmap": "refmap1.json"}'.bytes
		modSource.read("refmap1.json") >> REFMAP.bytes
		modSource.read("config2.json") >> '{"refmap": "refmap2.json"}'.bytes
		modSource.read("refmap2.json") >> REFMAP.bytes

		when:
		def configs = MixinConfiguration.fromMod(mod)

		then:
		configs.size() == 2
		configs[0].refmap().refmapPath() == "refmap1.json"
		configs[1].refmap().refmapPath() == "refmap2.json"
	}

	static MixinRefmap.NamespacePair NAMESPACE = new MixinRefmap.NamespacePair("named", "intermediary")
	@Language("JSON")
	static String REFMAP ='''
{
  "mappings": {
    "net/fabricmc/fabric/mixin/block/ChunkSectionBlockStateCounterMixin": {
      "Lnet/minecraft/block/BlockState;isAir()Z": "Lnet/minecraft/class_2680;method_26215()Z",
      "accept(Lnet/minecraft/block/BlockState;I)V": "Lnet/minecraft/class_2826$class_6869;method_40155(Lnet/minecraft/class_2680;I)V",
      "net/minecraft/world/chunk/ChunkSection$BlockStateCounter": "net/minecraft/class_2826$class_6869"
    }
  },
  "data": {
    "named:intermediary": {
      "net/fabricmc/fabric/mixin/block/ChunkSectionBlockStateCounterMixin": {
        "Lnet/minecraft/block/BlockState;isAir()Z": "Lnet/minecraft/class_2680;method_26215()Z",
        "accept(Lnet/minecraft/block/BlockState;I)V": "Lnet/minecraft/class_2826$class_6869;method_40155(Lnet/minecraft/class_2680;I)V",
        "net/minecraft/world/chunk/ChunkSection$BlockStateCounter": "net/minecraft/class_2826$class_6869"
      }
    }
  }
}'''
}