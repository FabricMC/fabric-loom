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

import org.intellij.lang.annotations.Language
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData
import net.fabricmc.loom.test.unit.service.mocks.MockTinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrField
import net.fabricmc.tinyremapper.api.TrMethod

import static org.mockito.Mockito.*

class AnnotationsDataRemapTest extends Specification {
	def "remap annotations data"() {
		given:
		def reader = new BufferedReader(new StringReader(ANNOTATIONS))
		def annotationsData = AnnotationsData.read(reader)

		def mockTr = new MockTinyRemapper()

		when(mockTr.remapper.map("pkg/Foo")).thenReturn("mapped/pkg/FooMapped")
		when(mockTr.remapper.map("pkg/Bar")).thenReturn("mapped/pkg/BarMapped")

		when(mockTr.remapper.map("pkg/Annotation1")).thenReturn("mapped/pkg/Annotation1Mapped")
		when(mockTr.remapper.map("pkg/Annotation2")).thenReturn("mapped/pkg/Annotation2Mapped")
		when(mockTr.remapper.map("pkg/Annotation3")).thenReturn("mapped/pkg/Annotation3Mapped")
		when(mockTr.remapper.map("pkg/Annotation4")).thenReturn("mapped/pkg/Annotation4Mapped")
		when(mockTr.remapper.map("pkg/Annotation5")).thenReturn("mapped/pkg/Annotation5Mapped")
		when(mockTr.remapper.map("pkg/Annotation6")).thenReturn("mapped/pkg/Annotation6Mapped")
		when(mockTr.remapper.map("pkg/Annotation7")).thenReturn("mapped/pkg/Annotation7Mapped")
		when(mockTr.remapper.map("pkg/Annotation8")).thenReturn("mapped/pkg/Annotation8Mapped")

		when(mockTr.remapper.map("pkg/MyEnum")).thenReturn("mapped/pkg/MyEnumMapped")

		when(mockTr.remapper.map("baz")).thenReturn("mapped/baz")

		when(mockTr.remapper.mapFieldName("pkg/Foo", "bar", "Lbaz;")).thenReturn("barRenamed")
		when(mockTr.remapper.mapMethodName("pkg/Foo", "bar", "()V")).thenReturn("barMethodRenamed")

		def mockClass = mock(TrClass.class)
		when(mockTr.trEnvironment.getClass("pkg/Foo")).thenReturn(mockClass)

		def mockField = mock(TrField.class)
		when(mockField.name).thenReturn("bar")
		when(mockField.desc).thenReturn("Lbaz;")
		when(mockClass.fields).thenReturn([mockField])

		def mockMethod = mock(TrMethod.class)
		when(mockMethod.name).thenReturn("bar")
		when(mockMethod.desc).thenReturn("()V")
		when(mockClass.methods).thenReturn([mockMethod])

		when:
		def remapped = annotationsData.remap(mockTr.tinyRemapper, "mapped")

		then:
		def json = AnnotationsData.GSON.newBuilder()
				.setPrettyPrinting()
				.create()
				.toJson(remapped.toJson())
				.replace("  ", "\t")

		json == REMAPPED_ANNOTATIONS.trim()
	}

	@Language("JSON")
	private static final String ANNOTATIONS = """
{
	"version": 1,
	"classes": {
		"pkg/Foo": {
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
				"bar:Lbaz;": {
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
				"barRenamed:Lmapped/baz;": {
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
