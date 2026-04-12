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

import org.intellij.lang.annotations.Language
import org.objectweb.asm.ClassReader
import org.objectweb.asm.TypeReference
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.validate.AnnotationsDataValidator

@SuppressWarnings("JsonStandardCompliance")
class AnnotationsDataValidatorTest extends Specification {
	private static final String TEST_CLASSES_PACKAGE_INTERNAL = "net/fabricmc/loom/test/unit/processor/classes/"

	private static String internalName(String simpleName) {
		return TEST_CLASSES_PACKAGE_INTERNAL + simpleName
	}

	private static ClassNode loadClassNode(String internalName) {
		ClassReader cr = new ClassReader(internalName)
		ClassNode cn = new ClassNode()
		cr.accept(cn, 0)
		return cn
	}

	class TestValidator extends AnnotationsDataValidator {
		final List<String> errors

		TestValidator(List<String> errors) {
			this.errors = errors
		}

		@Override
		protected ClassNode getClass(String name, boolean includeLibraries) {
			try {
				return loadClassNode(name)
			} catch (Exception ignored) {
				return null
			}
		}

		@Override
		protected void error(String message, Object... args) {
			errors << formatLog(message, args)
		}

		private String formatLog(String message, Object... args) {
			if (message == null) return ""
			if (!args || args.length == 0) return message
			StringBuilder sb = new StringBuilder()
			int last = 0
			int argIdx = 0
			while (true) {
				int idx = message.indexOf("{}", last)
				if (idx == -1) {
					sb.append(message.substring(last))
					break
				}
				sb.append(message.substring(last, idx))
				if (argIdx < args.length) {
					sb.append(String.valueOf(args[argIdx++]))
				} else {
					sb.append("{}")
				}
				last = idx + 2
			}
			return sb.toString()
		}
	}

	def "valid: removing an existing annotation should pass (class, field, method, param)"() {
		given:
		String classInternal = internalName("ClassWithAnnotations")
		String annInternal = internalName("AnnPresent")
		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "remove": [
                "${annInternal}"
            ],
            "fields": {
                "bar:I": {
                    "remove": ["${annInternal}"]
                }
            },
            "methods": {
                "method(Ljava/lang/String;)V": {
                    "remove": ["${annInternal}"],
                    "params": {
                        "0": {
                            "remove": ["${annInternal}"]
                        }
                    }
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: removing type parameter annotation on implements and return type"() {
		given:
		String classInternal = internalName("ClassWithImplements")
		String classWithReturnTypeInternal = internalName("ClassWithReturnType")
		String annInternal = internalName("AnnPresent")
		def implTypeRef = TypeReference.newSuperTypeReference(0).value
		def returnTypeRef = TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "type_remove": [
                {
                    "name": "${annInternal}",
                    "type_ref": ${implTypeRef},
                    "type_path": ""
                }
            ]
        },
        "${classWithReturnTypeInternal}": {
			"methods": {
                "annotatedGenericReturn()Ljava/util/List;": {
                    "type_remove": [
                        {
                            "name": "${annInternal}",
                            "type_ref": ${returnTypeRef},
                            "type_path": "0;"
                        }
                    ]
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: adding annotations and type annotations to class, field, method, and parameter"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String genericInternal = internalName("AdvancedGenericTargetClass")
		String addAnn = internalName("AnnAdd")
		def classTypeParamRef = TypeReference.newTypeParameterReference(TypeReference.CLASS_TYPE_PARAMETER, 0).value
		def fieldTypeRef = TypeReference.newTypeReference(TypeReference.FIELD).value
		def methodReturnRef = TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "add": [
                {
                    "desc": "L${addAnn};"
                }
            ],
            "fields": {
                "otherField:I": {
                    "add": [
                        {
                            "desc": "L${addAnn};"
                        }
                    ],
                    "type_add": [
                        {
                            "desc": "L${addAnn};",
                            "type_ref": ${fieldTypeRef},
                            "type_path": ""
                        }
                    ]
                }
            },
            "methods": {
                "otherMethodWithParams(I)V": {
                    "add": [
                        {
                            "desc": "L${addAnn};"
                        }
                    ],
                    "type_add": [
                        {
                            "desc": "L${addAnn};",
                            "type_ref": ${methodReturnRef},
                            "type_path": ""
                        }
                    ],
                    "params": {
                        "0": {
                            "add": [
                                {
                                    "desc": "L${addAnn};"
                                }
                            ]
                        }
                    }
                }
            }
        },
        "${genericInternal}": {
            "type_add": [
                {
                    "desc": "L${addAnn};",
                    "type_ref": ${classTypeParamRef},
                    "type_path": ""
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "invalid: removing an annotation that isn't present should produce an error"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String annNotPresentInternal = internalName("AnnNotPresent")
		@Language("JSON")
				String json = """
{
	"version": 1,
	"classes": {
		"${classInternal}": {
			"remove": [
				"${annNotPresentInternal}"
			]
		}
	},
	"namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expected = "Trying to remove annotation ${annNotPresentInternal} from ${classInternal} but it's not present"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expected)
	}

	def "invalid: adding an annotation already present should produce an error"() {
		given:
		String classInternal = internalName("ClassWithAnnotations")
		String annPresentInternal = internalName("AnnPresent")
		@Language("JSON")
				String json = """
{
	"version": 1,
	"classes": {
		"${classInternal}": {
			"add": [
				{
					"desc": "L${annPresentInternal};"
				}
			]
		}
	},
	"namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expected = "Trying to add annotation L${annPresentInternal}; to ${classInternal} but it's already present"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expected)
	}

	def "invalid: adding/removing annotations to fields/methods/classes/params that don't exist should produce errors"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String annAddInternal = internalName("AnnAdd")
		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "fields": {
                "noSuchField:I": {
                    "remove": ["${annAddInternal}"]
                }
            },
            "methods": {
                "otherMethod()V": {
                    "add": [
                        {"desc": "L${annAddInternal};"}
                    ],
                    "parameters": {
                        "5": {
                            "add": [{"desc": "L${annAddInternal};"}]
                        }
                    }
                },
                "noSuchMethod()V": {
                    "add": [
                        {"desc": "L${annAddInternal};"}
                    ]
                }
            }
        },
        "net/fabricmc/loom/test/unit/processor/classes/NonExistentClass": {
            "add": [
                {"desc": "L${annAddInternal};"}
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expectedFieldErr = "No such target field: ${classInternal}.noSuchField:I"
		String expectedMethodErr = "No such target method: ${classInternal}.noSuchMethod()V"
		String expectedParamErr = "Invalid parameter index: 5 for method: ${classInternal}.otherMethod()V"
		String expectedClassNotFound = "No such target class: net/fabricmc/loom/test/unit/processor/classes/NonExistentClass"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expectedFieldErr)
		errors.contains(expectedMethodErr)
		errors.contains(expectedParamErr)
		errors.contains(expectedClassNotFound)
	}

	def "invalid: adding annotation with attribute that doesn't exist; valid: adding with attribute that does exist"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String annAttr = internalName("AnnWithAttr")
		@Language("JSON")
				String jsonBad = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "add": [
                {
                    "desc": "L${annAttr};",
                    "values": {
                        "nonexistent": { "type": "int", "value": 5 }
                    }
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def readerBad = new BufferedReader(new StringReader(jsonBad))
		def dataBad = AnnotationsData.read(readerBad)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expectedBad = "Unknown annotation attribute: ${annAttr}.nonexistent"
		String expectedBadMissing = "Annotation applied to ${classInternal} is missing required attributes: [value]"

		when:
		boolean okBad = validator.checkData(dataBad)

		then:
		!okBad
		errors.contains(expectedBad)
		errors.contains(expectedBadMissing)

		when: "now add correct attribute"
		errors.clear()
		@Language("JSON")
				String jsonGood = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "add": [
                {
                    "desc": "L${annAttr};",
                    "values": {
                        "value": { "type": "int", "value": 5 }
                    }
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def readerGood = new BufferedReader(new StringReader(jsonGood))
		def dataGood = AnnotationsData.read(readerGood)

		boolean okGood = validator.checkData(dataGood)

		then:
		okGood
		errors.isEmpty()
	}

	def "invalid: adding annotation with attribute value of wrong type"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String annAttr = internalName("AnnWithAttr")
		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "add": [
                {
                    "desc": "L${annAttr};",
                    "values": {
                        "value": { "type": "string", "value": "not-an-int" }
                    }
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expected = "Annotation value is of type java.lang.String, expected int for attribute value"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expected)
	}

	def "invalid: adding/removing type-parameters that don't exist"() {
		given:
		String classInternal = internalName("ClassWithGenericParams")
		String annInternal = internalName("AnnAdd")

		def classTypeParamRef = TypeReference.newTypeParameterReference(TypeReference.CLASS_TYPE_PARAMETER, 5).value
		def methodTypeParamRef = TypeReference.newTypeParameterReference(TypeReference.METHOD_TYPE_PARAMETER, 3).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "type_add": [
                {
                    "desc": "L${annInternal};",
                    "type_ref": ${classTypeParamRef},
                    "type_path": ""
                }
            ],
            "methods": {
                "methodWithTypeParam()V": {
                    "type_add": [
                        {
                            "desc": "L${annInternal};",
                            "type_ref": ${methodTypeParamRef},
                            "type_path": ""
                        }
                    ]
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expectedClassTP = "Invalid type reference for class type annotation: ${classTypeParamRef}, formal parameter index 5 out of bounds"
		String expectedMethodTP = "Invalid type reference for method type annotation: ${methodTypeParamRef}, formal parameter index 3 out of bounds"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expectedClassTP)
		errors.contains(expectedMethodTP)
	}

	def "valid: type annotation on class type parameter passes"() {
		given:
		String classInternal = internalName("ClassWithGenericParams")
		String ann = internalName("AnnPresent")
		def typeRef = TypeReference.newTypeParameterReference(TypeReference.CLASS_TYPE_PARAMETER, 1).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "type_add": [
                {
                    "desc": "L${ann};",
                    "type_ref": ${typeRef},
                    "type_path": ""
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: type annotation on class type parameter bound passes"() {
		given:
		String classInternal = internalName("SelfGenericInterface")
		String ann = internalName("AnnPresent")
		def typeRef = TypeReference.newTypeParameterBoundReference(TypeReference.CLASS_TYPE_PARAMETER_BOUND, 0, 0).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "type_add": [
                {
                    "desc": "L${ann};",
                    "type_ref": ${typeRef},
                    "type_path": ""
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: type annotation on extends (superclass) passes"() {
		given:
		String classInternal = internalName("ClassWithGenericParams")
		String ann = internalName("AnnPresent")
		def extendsRef = TypeReference.newSuperTypeReference(-1).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "type_add": [
                {
                    "desc": "L${ann};",
                    "type_ref": ${extendsRef},
                    "type_path": ""
                }
            ]
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: field type annotation with type path passes"() {
		given:
		String classInternal = internalName("ClassWithFieldTypes")
		String ann = internalName("AnnPresent")
		def fieldRef = TypeReference.newTypeReference(TypeReference.FIELD).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "fields": {
                "listField:Ljava/util/List;": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${fieldRef},
                            "type_path": "0;"
                        }
                    ]
                },
                "arrayField:[Ljava/lang/String;": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${fieldRef},
                            "type_path": "["
                        }
                    ]
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "valid: method return type annotation with type path passes"() {
		given:
		String classInternal = internalName("ClassWithReturnType")
		String ann = internalName("AnnPresent")
		def returnRef = TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "methods": {
                "genericReturn()Ljava/util/List;": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${returnRef},
                            "type_path": "0;"
                        }
                    ]
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "receiver type annotation: valid on instance methods, invalid on static methods and constructors"() {
		given:
		String classInternal = internalName("ClassWithReceiverAndParams")
		String ann = internalName("AnnPresent")
		def receiverRef = TypeReference.newTypeReference(TypeReference.METHOD_RECEIVER).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "methods": {
                "instanceMethod(ILjava/lang/String;)V": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${receiverRef},
                            "type_path": ""
                        }
                    ]
                },
                "staticMethod(I)V": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${receiverRef},
                            "type_path": ""
                        }
                    ]
                },
                "<init>(I)V": {
                    "type_add": [
                        {
                            "desc": "L${ann};",
                            "type_ref": ${receiverRef},
                            "type_path": ""
                        }
                    ]
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expectedStaticReceiverErr = "Invalid type reference for method type annotation: ${receiverRef}, method receiver used in a static context"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.size() == 2
		errors.contains(expectedStaticReceiverErr)
	}

	def "valid: type annotation on method formal parameter passes"() {
		given:
		String classInternal = internalName("ClassWithReceiverAndParams")
		String ann = internalName("AnnPresent")
		def formalParamRef = TypeReference.newFormalParameterReference(0).value

		@Language("JSON")
				String json = """
{
    "version": 1,
    "classes": {
        "${classInternal}": {
            "methods": {
                "instanceMethod(ILjava/lang/String;)V": {
                    "params": {
                        "0": {
                            "type_add": [
                                {
                                    "desc": "L${ann};",
                                    "type_ref": ${formalParamRef},
                                    "type_path": ""
                                }
                            ]
                        }
                    }
                }
            }
        }
    },
    "namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		when:
		boolean ok = validator.checkData(data)

		then:
		ok
		errors.isEmpty()
	}

	def "invalid: type annotation referring to out-of-bounds checked-exception index should produce an error"() {
		given:
		String classInternal = internalName("ClassWithoutAnnotations")
		String annTypeInternal = internalName("AnnAdd")
		int typeRef = TypeReference.newExceptionReference(0).value
		@Language("JSON")
				String json = """
{
	"version": 1,
	"classes": {
		"${classInternal}": {
			"type_add": [
				{
					"desc": "L${annTypeInternal};",
					"type_ref": ${typeRef},
					"type_path": "["
				}
			],
			"methods": {
				"otherMethod()V": {
					"type_add": [
						{
							"desc": "L${annTypeInternal};",
							"type_ref": ${typeRef},
							"type_path": ""
						}
					]
				}
			}
		}
	},
	"namespace": "test"
}
"""
		def reader = new BufferedReader(new StringReader(json))
		def data = AnnotationsData.read(reader)

		List<String> errors = []
		def validator = new TestValidator(errors)

		String expectedClassTypeRefErr = "Invalid type reference for class type annotation: ${typeRef}"
		String expectedMethodTypeRefErr = "Invalid type reference for method type annotation: ${typeRef}, exception index 0 out of bounds"

		when:
		boolean ok = validator.checkData(data)

		then:
		!ok
		errors.contains(expectedClassTypeRefErr)
		errors.contains(expectedMethodTypeRefErr)
	}
}
