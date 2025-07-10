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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData

class AnnotationsLayerTest extends Specification {
	def "read annotations"() {
		when:
		def reader = new BufferedReader(new StringReader(ANNOTATIONS))
		def annotationsData = AnnotationsData.read(reader)

		then:
		annotationsData.classes().size() == 2
		annotationsData.classes()["pkg/Foo"].annotationsToRemove() == [
			"pkg/Annotation1",
			"pkg/Annotation2",
			"pkg/Annotation3"
		] as Set
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[0].desc == "pkg/Annotation4"
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[1] == 42
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[3] == Type.getType("Ljava/lang/String;")
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[5] == ["pkg/MyEnum", "VALUE"] as String[]
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[7] instanceof AnnotationNode && annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[7].desc == "pkg/Annotation6"
		annotationsData.classes()["pkg/Foo"].annotationsToAdd()[1].values[9] == [1, 2]
		annotationsData.classes()["pkg/Foo"].typeAnnotationsToAdd()[0].typePath.toString() == "["
		annotationsData.classes()["pkg/Foo"].fields().keySet().first() == "bar:Lbaz;"
		annotationsData.classes()["pkg/Foo"].methods().keySet().first() == "bar()V"
		annotationsData.classes()["pkg/Foo"].methods().values().first().typeAnnotationsToAdd().isEmpty()
	}

	def "write annotations"() {
		when:
		def reader = new BufferedReader(new StringReader(ANNOTATIONS))
		def annotationsData = AnnotationsData.read(reader)
		def json = AnnotationsData.GSON.newBuilder()
				.setPrettyPrinting()
				.create()
				.toJson(annotationsData.toJson())
				.replace("  ", "\t")

		then:
		json == ANNOTATIONS.trim()
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
					"desc": "pkg/Annotation4"
				},
				{
					"desc": "pkg/Annotation5",
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
							"owner": "pkg/MyEnum",
							"name": "VALUE"
						},
						"ann": {
							"type": "annotation",
							"desc": "pkg/Annotation6"
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
					"desc": "pkg/Annotation7",
					"type_ref": 22,
					"type_path": "["
				}
			],
			"fields": {
				"bar:Lbaz;": {
					"remove": [
						"java/lang/Deprecated"
					]
				}
			},
			"methods": {
				"bar()V": {
					"remove": [
						"java/lang/Deprecated"
					]
				}
			}
		},
		"pkg/Bar": {
			"add": [
				{
					"desc": "pkg/Annotation1"
				}
			]
		}
	}
}
"""
}
