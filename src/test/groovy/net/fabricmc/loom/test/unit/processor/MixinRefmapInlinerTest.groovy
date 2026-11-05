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

package net.fabricmc.loom.test.unit.processor

import java.util.stream.Stream

import groovy.transform.CompileStatic
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import spock.lang.Specification

import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinReferenceRemapper
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinReferenceRemapperImpl
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinRefmapInlinerApplyVisitorProvider
import net.fabricmc.loom.configuration.mods.dependency.refmap.MixinRefmapInlinerClassVisitor
import net.fabricmc.loom.util.fmj.mixin.MixinRefmap
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.TinyRemapper

class MixinRefmapInlinerTest extends Specification {
	static String className = "net/fabricmc/loom/test/unit/processor/MixinRefmapInlinerClassVisitorTest\$ExampleClass"

	def "Missing refmaps leave references unchanged"() {
		when:
		def remapper = MixinReferenceRemapperImpl.createFromRefmaps("named", "intermediary", Stream.of(null))

		then:
		remapper.remapReference("ExampleMixin", "exampleMethod") == "exampleMethod"
	}

	def "Missing refmaps do not prevent other refmaps from being applied"() {
		given:
		def references = new MixinRefmap.ReferenceMappingData([exampleMethod: "method_123"])
		def mappings = new MixinRefmap.MixinMappingData([ExampleMixin: references])
		def refmap = new MixinRefmap("example.refmap.json", mappings, [:])

		when:
		def remapper = MixinReferenceRemapperImpl.createFromRefmaps("named", "intermediary", Stream.of(null, refmap, null))

		then:
		remapper.remapReference("ExampleMixin", "exampleMethod") == "method_123"
		remapper.remapReference("ExampleMixin", "unmappedMethod") == "unmappedMethod"
	}

	def "Refmaps are inlined only for selected input tags: #tagNames"() {
		given:
		def tinyRemapper = TinyRemapper.newRemapper().build()
		def selectedTag = tinyRemapper.createInputTag()
		def unselectedTag = tinyRemapper.createInputTag()
		def tags = [selected: selectedTag, unselected: unselectedTag]
		def inputTags = tagNames.collect { tags[it] } as InputTag[]
		def remapper = Stub(MixinReferenceRemapper)
		def provider = new MixinRefmapInlinerApplyVisitorProvider(remapper, {
			it == selectedTag
		})
		def next = new ClassNode()

		when:
		def visitor = provider.insertApplyVisitor(null, next, inputTags)

		then:
		(visitor instanceof MixinRefmapInlinerClassVisitor) == shouldInline
		shouldInline || visitor.is(next)

		cleanup:
		tinyRemapper.finish()

		where:
		tagNames | shouldInline
		["selected"] | true
		["unselected"] | false
		["selected", "unselected"] | true
		["unselected", "selected"] | true
		[] | false
	}

	def "MixinRefmapInlinerClassVisitor"() {
		given:
		def remapper = [
			remapReference: { String mixinClassName, String reference ->
				return switch (reference) {
							case "MixinRefmapInlinerClassVisitor" -> "RemappedClassName"
							case "injectExample" -> "remappedInjectExample"
							case "HEAD" -> "INJECT"
							default -> reference
						}
			}
		] as MixinReferenceRemapper

		def remappedNode = new ClassNode()
		def classVisitor = new MixinRefmapInlinerClassVisitor(remapper, remappedNode)

		when:
		def classReader = new ClassReader(getClassBytes(ExampleClass))
		classReader.accept(classVisitor, 0)

		def text = textify(remappedNode)

		then:
		text.contains('@Lorg/spongepowered/asm/mixin/Mixin;(targets={"RemappedClassName"})')
		text.contains('@Lorg/spongepowered/asm/mixin/injection/Inject;(method={"remappedInjectExample"}, at={@Lorg/spongepowered/asm/mixin/injection/At;(value="INJECT")})')
	}

	static byte[] getClassBytes(Class<?> clazz) {
		try (def input = clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class")) {
			return input.bytes
		}
	}

	static String textify(ClassNode classNode) {
		def stringWriter = new StringWriter()
		def printWriter = new PrintWriter(stringWriter)
		def textifier = new Textifier()
		def traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter)
		classNode.accept(traceClassVisitor)
		return stringWriter.toString()
	}

	@Mixin(targets = "MixinRefmapInlinerClassVisitor")
	@CompileStatic
	class ExampleClass {
		@Inject(method = "injectExample", at = @At("HEAD"))
		void injectExample() {
		}
	}
}
