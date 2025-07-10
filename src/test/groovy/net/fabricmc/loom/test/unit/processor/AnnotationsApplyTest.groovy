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

import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData
import net.fabricmc.loom.configuration.providers.minecraft.AnnotationsApplyVisitor

class AnnotationsApplyTest extends Specification {
	def "apply annotations"() {
		given:
		def annotationData = AnnotationsData.read(new StringReader(ANNOTATIONS_DATA))

		def annotatedNode1 = new ClassNode()
		def classVisitor1 = new AnnotationsApplyVisitor.AnnotationsApplyClassVisitor(annotatedNode1, 'net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass1', annotationData)
		def annotatedNode2 = new ClassNode()
		def classVisitor2 = new AnnotationsApplyVisitor.AnnotationsApplyClassVisitor(annotatedNode2, 'net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass2', annotationData)

		when:
		def classReader1 = new ClassReader(getClassBytes(ExampleClass1))
		classReader1.accept(classVisitor1, ClassReader.SKIP_CODE)

		def text1 = textifyImportantPartsOfClass(annotatedNode1)
		def field1Text = textify(annotatedNode1.fields.find { it.name == "field1" })
		def field2Text = textify(annotatedNode1.fields.find { it.name == "field2" })
		def method1Text = textify(annotatedNode1.methods.find { it.name == "method1" })
		def method2Text = textify(annotatedNode1.methods.find { it.name == "method2" })

		//noinspection GrDeprecatedAPIUsage
		def classReader2 = new ClassReader(getClassBytes(ExampleClass2))
		classReader2.accept(classVisitor2, ClassReader.SKIP_CODE)

		def text2 = textifyImportantPartsOfClass(annotatedNode2)

		then:
		text1 == EXPECTED_TEXT1
		text2 == EXPECTED_TEXT2
		field1Text == EXPECTED_FIELD1
		field2Text == EXPECTED_FIELD2
		method1Text == EXPECTED_METHOD1
		method2Text == EXPECTED_METHOD2
	}

	static byte[] getClassBytes(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class").withCloseable {
			it.bytes
		}
	}

	static String textify(FieldNode field) {
		def cv = new TraceClassVisitor(null)
		field.accept(cv)
		def sw = new StringWriter()
		cv.p.print(new PrintWriter(sw))
		return sw.toString()
	}

	static String textify(MethodNode method) {
		def cv = new TraceClassVisitor(null)
		method.accept(cv)
		def sw = new StringWriter()
		cv.p.print(new PrintWriter(sw))
		return sw.toString()
	}

	static String textifyImportantPartsOfClass(ClassNode clazz) {
		ClassNode strippedClass = new ClassNode()
		clazz.accept(strippedClass)
		strippedClass.version = 52
		strippedClass.fields.clear()
		strippedClass.methods.clear()
		def stringWriter = new StringWriter()
		def printWriter = new PrintWriter(stringWriter)
		def textifier = new Textifier()
		def traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter)
		strippedClass.accept(traceClassVisitor)
		return stringWriter.toString()
	}

	@CompileStatic
	@ApiStatus.Internal
	class ExampleClass1 {
		@Deprecated
		String field1
		@Nullable
		String field2

		@Deprecated
		void method1(@NotNull String parameter) {
		}

		void method2() {
		}
	}

	@CompileStatic
	@Deprecated
	class ExampleClass2 {
	}

	@Language("JSON")
	private static final String ANNOTATIONS_DATA = '''
{
	"version": 1,
	"classes": {
		"net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass1": {
			"remove": [
				"org/jetbrains/annotations/ApiStatus$Internal"
			],
			"add": [
				{
					"desc": "Ljava/lang/Deprecated;"
				}
			],
			"fields": {
				"field1:Ljava/lang/String;": {
					"remove": [
						"java/lang/Deprecated"
					],
					"add": [
						{
							"desc": "Lorg/jetbrains/annotations/ApiStatus$Internal;"
						}
					]
				},
				"field2:Ljava/lang/String;": {
					"remove": [
						"org/jetbrains/annotations/Nullable"
					],
					"add": [
						{
							"desc": "Ljava/lang/Deprecated;"
						},
						{
							"desc": "Lorg/jetbrains/annotations/ApiStatus$Internal;"
						}
					]
				}
			},
			"methods": {
				"method1(Ljava/lang/String;)V": {
					"remove": [
						"java/lang/Deprecated"
					],
					"add": [
						{
							"desc": "Lorg/jetbrains/annotations/ApiStatus$OverrideOnly;"
						}
					],
					"parameters": {
						"0": {
							"remove": [
								"org/jetbrains/annotations/NotNull"
							],
							"add": [
								{
									"desc": "Lorg/jetbrains/annotations/UnknownNullability;"
								}
							]
						}
					}
				},
				"method2()V": {
					"add": [
						{
							"desc": "Ljava/lang/Deprecated;"
						},
						{
							"desc": "Lorg/jetbrains/annotations/Contract;",
							"values": {
								"pure": {
									"type": "boolean",
									"value": true
								}
							}
						}
					]
				}
			}
		},
		"net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass2": {
			"remove": [
				"java/lang/Deprecated"
			],
			"add": [
				{
					"desc": "Lorg/jetbrains/annotations/ApiStatus$Internal;"
				}
			]
		}
	}
}
'''

	private static final String EXPECTED_TEXT1 = '''// class version 52.0 (52)
// DEPRECATED
// access flags 0x20021
public class net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass1 implements groovy/lang/GroovyObject {

  // compiled from: AnnotationsApplyTest.groovy

  @Ljava/lang/Deprecated;() // invisible
  // access flags 0x1
  public INNERCLASS net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass1 net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest ExampleClass1
}
'''

	private static final String EXPECTED_TEXT2 = '''// class version 52.0 (52)
// access flags 0x21
public class net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass2 implements groovy/lang/GroovyObject {

  // compiled from: AnnotationsApplyTest.groovy

  @Lorg/jetbrains/annotations/ApiStatus$Internal;() // invisible
  // access flags 0x1
  public INNERCLASS net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest$ExampleClass2 net/fabricmc/loom/test/unit/processor/AnnotationsApplyTest ExampleClass2
}
'''

	private static final String EXPECTED_FIELD1 = '''
  // access flags 0x2
  private Ljava/lang/String; field1
  @Lorg/jetbrains/annotations/ApiStatus$Internal;() // invisible
'''

	private static final String EXPECTED_FIELD2 = '''
  // DEPRECATED
  // access flags 0x20002
  private Ljava/lang/String; field2
  @Ljava/lang/Deprecated;() // invisible
  @Lorg/jetbrains/annotations/ApiStatus$Internal;() // invisible
'''

	private static final String EXPECTED_METHOD1 = '''
  // access flags 0x1
  public method1(Ljava/lang/String;)V
  @Lorg/jetbrains/annotations/ApiStatus$OverrideOnly;() // invisible
    // annotable parameter count: 1 (invisible)
    @Lorg/jetbrains/annotations/UnknownNullability;() // invisible, parameter 0
'''

	private static final String EXPECTED_METHOD2 = '''
  // DEPRECATED
  // access flags 0x20001
  public method2()V
  @Ljava/lang/Deprecated;() // invisible
  @Lorg/jetbrains/annotations/Contract;(pure=true) // invisible
'''
}
