/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl
import net.fabricmc.loom.configuration.providers.mappings.file.FileMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentMappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.utils.MavenFileSpec
import net.fabricmc.loom.util.ClosureAction

class LayeredMappingSpecBuilderTest extends Specification {
	def "simple mojmap" () {
		when:
		def spec = layered {
			officialMojangMappings()
		}
		def layers = spec.layers()
		then:
		layers.size() == 2
		spec.version == "layered+hash.2198"
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == MojangMappingsSpec
	}

	def "simple mojmap with parchment" () {
		when:
		def dep = "I like cake"
		def spec = layered() {
			officialMojangMappings()
			parchment(dep)
		}
		def layers = spec.layers()
		def parchment = layers[2] as ParchmentMappingsSpec
		then:
		spec.version == "layered+hash.863752751"
		layers.size() == 3
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == MojangMappingsSpec
		layers[2].class == ParchmentMappingsSpec
		(parchment.fileSpec() as MavenFileSpec).dependencyNotation() == "I like cake"
		parchment.removePrefix() == true
	}

	def "simple mojmap with parchment keep prefix" () {
		when:
		def spec = layered() {
			officialMojangMappings()
			parchment("I like cake") {
				it.removePrefix = false
			}
		}
		def layers = spec.layers()
		def parchment = layers[2] as ParchmentMappingsSpec
		then:
		spec.version == "layered+hash.863752757"
		layers.size() == 3
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == MojangMappingsSpec
		layers[2].class == ParchmentMappingsSpec
		(parchment.fileSpec() as MavenFileSpec).dependencyNotation() == "I like cake"
		parchment.removePrefix() == false
	}

	def "simple mojmap with parchment keep prefix alternate hash" () {
		when:
		def spec = layered {
			officialMojangMappings()
			parchment("I really like cake") {
				it.removePrefix = false
			}
		}
		def layers = spec.layers()
		def parchment = layers[2] as ParchmentMappingsSpec
		then:
		spec.version == "layered+hash.1144427140"
		layers.size() == 3
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == MojangMappingsSpec
		layers[2].class == ParchmentMappingsSpec
		(parchment.fileSpec() as MavenFileSpec).dependencyNotation() == "I really like cake"
		parchment.removePrefix() == false
	}

	def "yarn through file mappings"() {
		when:
		def spec = layered {
			mappings("net.fabricmc:yarn:1.18.1+build.1:v2")
		}
		def layers = spec.layers()
		then:
		spec.version == "layered+hash.1133958200"
		layers.size() == 2
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == FileMappingsSpec
		((layers[1] as FileMappingsSpec).fileSpec() as MavenFileSpec).dependencyNotation() == "net.fabricmc:yarn:1.18.1+build.1:v2"
	}

	LayeredMappingSpec layered(@DelegatesTo(LayeredMappingSpecBuilderImpl) Closure cl) {
		LayeredMappingSpecBuilderImpl builder = new LayeredMappingSpecBuilderImpl()
		new ClosureAction(cl).execute(builder)
		return builder.build()
	}
}
