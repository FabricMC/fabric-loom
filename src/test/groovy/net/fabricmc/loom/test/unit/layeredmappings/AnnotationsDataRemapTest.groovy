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

package net.fabricmc.loom.test.unit.layeredmappings

import java.nio.file.Path

import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyRemapper

class AnnotationsDataRemapTest extends Specification {
	def "remap annotations data"() {
		given:
		def reader = new BufferedReader(new StringReader(ANNOTATIONS))
		def annotationsData = AnnotationsData.read(reader)

		def remapper = TinyRemapper.newRemapper()
				.withMappings { mappings ->
					mappings.acceptClass('net/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest$Foo', 'mapped/pkg/FooMapped')
					mappings.acceptClass('pkg/Bar', 'mapped/pkg/BarMapped')

					mappings.acceptClass('pkg/Annotation1', 'mapped/pkg/Annotation1Mapped')
					mappings.acceptClass('pkg/Annotation2', 'mapped/pkg/Annotation2Mapped')
					mappings.acceptClass('pkg/Annotation3', 'mapped/pkg/Annotation3Mapped')
					mappings.acceptClass('pkg/Annotation4', 'mapped/pkg/Annotation4Mapped')
					mappings.acceptClass('pkg/Annotation5', 'mapped/pkg/Annotation5Mapped')
					mappings.acceptClass('pkg/Annotation6', 'mapped/pkg/Annotation6Mapped')
					mappings.acceptClass('pkg/Annotation7', 'mapped/pkg/Annotation7Mapped')
					mappings.acceptClass('pkg/Annotation8', 'mapped/pkg/Annotation8Mapped')

					mappings.acceptClass('pkg/MyEnum', 'mapped/pkg/MyEnumMapped')

					mappings.acceptClass('baz', 'mapped/baz')

					mappings.acceptField(new IMappingProvider.Member('net/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest$Foo', 'bar', 'Lnet/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest$Foo;'), 'barRenamed')
					mappings.acceptMethod(new IMappingProvider.Member('net/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest$Foo', 'bar', '()V'), 'barMethodRenamed')
				}
				.build()

		remapper.readClassPath(Path.of(Foo.class.protectionDomain.codeSource.location.toURI()))

		when:
		def remapped = annotationsData.remap(remapper, "mapped")

		then:
		def json = AnnotationsData.GSON.newBuilder()
				.setPrettyPrinting()
				.create()
				.toJson(remapped.toJson())
				.replace("  ", "\t")

		json == REMAPPED_ANNOTATIONS.trim()
	}

	@CompileStatic
	class Foo {
		Foo bar

		void bar() {
		}
	}

	@Language("JSON")
	private static final String ANNOTATIONS = """
{
	"version": 1,
	"classes": {
		"net/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest${'$'}Foo": {
			"remove": [
				"pkg/Annotation1",
				"pkg/Annotation2",
				"pkg/Annotation3"
			],
			"add": [
				{
					"desc": "Lpkg/Annotation4;"
				},
				{
					"desc": "Lpkg/Annotation5;",
					"values": {
						"foo": {
							"type": "int",
							"value": 42
						},
						"bar": {
							"type": "class",
							"value": "Ljava/lang/String;"
						},
						"baz": {
							"type": "enum_constant",
							"owner": "Lpkg/MyEnum;",
							"name": "VALUE"
						},
						"ann": {
							"type": "annotation",
							"desc": "Lpkg/Annotation6;"
						},
						"arr": {
							"type": "array",
							"value": [
								{
									"type": "int",
									"value": 1
								},
								{
									"type": "int",
									"value": 2
								}
							]
						}
					}
				}
			],
			"type_add": [
				{
					"desc": "Lpkg/Annotation7;",
					"type_ref": 22,
					"type_path": "["
				}
			],
			"fields": {
				"bar:Lnet/fabricmc/loom/test/unit/layeredmappings/AnnotationsDataRemapTest${'$'}Foo;": {
					"remove": [
						"pkg/Annotation8"
					]
				}
			},
			"methods": {
				"bar()V": {
					"remove": [
						"pkg/Annotation8"
					]
				}
			}
		},
		"pkg/Bar": {
			"add": [
				{
					"desc": "Lpkg/Annotation1;"
				}
			]
		}
	},
	"namespace": "someNamespace"
}
"""
	@Language("JSON")
	private static final String REMAPPED_ANNOTATIONS = """
{
	"version": 1,
	"classes": {
		"mapped/pkg/FooMapped": {
			"remove": [
				"mapped/pkg/Annotation1Mapped",
				"mapped/pkg/Annotation2Mapped",
				"mapped/pkg/Annotation3Mapped"
			],
			"add": [
				{
					"desc": "Lmapped/pkg/Annotation4Mapped;"
				},
				{
					"desc": "Lmapped/pkg/Annotation5Mapped;",
					"values": {
						"foo": {
							"type": "int",
							"value": 42
						},
						"bar": {
							"type": "class",
							"value": "Ljava/lang/String;"
						},
						"baz": {
							"type": "enum_constant",
							"owner": "Lmapped/pkg/MyEnumMapped;",
							"name": "VALUE"
						},
						"ann": {
							"type": "annotation",
							"desc": "Lmapped/pkg/Annotation6Mapped;"
						},
						"arr": {
							"type": "array",
							"value": [
								{
									"type": "int",
									"value": 1
								},
								{
									"type": "int",
									"value": 2
								}
							]
						}
					}
				}
			],
			"type_add": [
				{
					"desc": "Lmapped/pkg/Annotation7Mapped;",
					"type_ref": 22,
					"type_path": "["
				}
			],
			"fields": {
				"barRenamed:Lmapped/pkg/FooMapped;": {
					"remove": [
						"mapped/pkg/Annotation8Mapped"
					]
				}
			},
			"methods": {
				"barMethodRenamed()V": {
					"remove": [
						"mapped/pkg/Annotation8Mapped"
					]
				}
			}
		},
		"mapped/pkg/BarMapped": {
			"add": [
				{
					"desc": "Lmapped/pkg/Annotation1Mapped;"
				}
			]
		}
	},
	"namespace": "mapped"
}
"""
}
