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

package net.fabricmc.loom.configuration.providers.mappings.extras.annotations.validate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.TypeAnnotationKey;
import net.fabricmc.loom.util.Constants;

public abstract class AnnotationsDataValidator {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationsDataValidator.class);

	@Nullable
	protected abstract ClassNode getClass(String name, boolean includeLibraries);

	@VisibleForTesting
	protected void error(String message, Object... args) {
		LOGGER.error(message, args);
	}

	public boolean checkData(AnnotationsData data) {
		boolean result = true;

		for (var classEntry : data.classes().entrySet()) {
			result &= checkClassData(classEntry.getKey(), classEntry.getValue());
		}

		return result;
	}

	private boolean checkClassData(String className, ClassAnnotationData classData) {
		ClassNode clazz = getClass(className, false);

		if (clazz == null) {
			error("No such target class: {}", className);
			return false;
		}

		boolean result = true;

		List<AnnotationNode> annotations = concatLists(clazz.visibleAnnotations, clazz.invisibleAnnotations);
		result &= checkAnnotationsToRemove(() -> className, classData.annotationsToRemove(), annotations);
		result &= checkAnnotationsToAdd(() -> className, classData.annotationsToAdd(), annotations);
		List<TypeAnnotationNode> typeAnnotations = concatLists(clazz.visibleTypeAnnotations, clazz.invisibleTypeAnnotations);
		result &= checkTypeAnnotationsToRemove(() -> className, classData.typeAnnotationsToRemove(), typeAnnotations);
		result &= checkTypeAnnotationsToAdd(() -> className, classData.typeAnnotationsToAdd(), typeAnnotations, typeAnnotation -> checkClassTypeAnnotation(typeAnnotation, clazz));

		for (var fieldEntry : classData.fields().entrySet()) {
			FieldNode field = findField(clazz, fieldEntry.getKey());

			if (field == null) {
				error("No such target field: {}.{}", className, fieldEntry.getKey());
				result = false;
				continue;
			}

			result &= checkGenericData(
					() -> className + "." + fieldEntry.getKey(),
					fieldEntry.getValue(),
					concatLists(field.visibleAnnotations, field.invisibleAnnotations),
					concatLists(field.visibleTypeAnnotations, field.invisibleTypeAnnotations),
					typeAnnotation -> checkFieldTypeAnnotation(typeAnnotation, field)
			);
		}

		for (var methodEntry : classData.methods().entrySet()) {
			MethodNode method = findMethod(clazz, methodEntry.getKey());

			if (method == null) {
				error("No such target method: {}.{}", className, methodEntry.getKey());
				result = false;
				continue;
			}

			result &= checkMethodData(() -> className + "." + methodEntry.getKey(), methodEntry.getValue(), className, method);
		}

		return result;
	}

	private boolean checkGenericData(Supplier<String> errorKey, GenericAnnotationData data, List<AnnotationNode> annotations, List<TypeAnnotationNode> typeAnnotations, TypeAnnotationChecker typeAnnotationChecker) {
		boolean result = true;
		result &= checkAnnotationsToRemove(errorKey, data.annotationsToRemove(), annotations);
		result &= checkAnnotationsToAdd(errorKey, data.annotationsToAdd(), annotations);
		result &= checkTypeAnnotationsToRemove(errorKey, data.typeAnnotationsToRemove(), typeAnnotations);
		result &= checkTypeAnnotationsToAdd(errorKey, data.typeAnnotationsToAdd(), typeAnnotations, typeAnnotationChecker);
		return result;
	}

	private boolean checkMethodData(Supplier<String> errorKey, MethodAnnotationData data, String className, MethodNode method) {
		boolean result = true;
		List<AnnotationNode> annotations = concatLists(method.visibleAnnotations, method.invisibleAnnotations);
		result &= checkAnnotationsToRemove(errorKey, data.annotationsToRemove(), annotations);
		result &= checkAnnotationsToAdd(errorKey, data.annotationsToAdd(), annotations);
		List<TypeAnnotationNode> typeAnnotations = concatLists(method.visibleTypeAnnotations, method.invisibleTypeAnnotations);
		result &= checkTypeAnnotationsToRemove(errorKey, data.typeAnnotationsToRemove(), typeAnnotations);
		result &= checkTypeAnnotationsToAdd(errorKey, data.typeAnnotationsToAdd(), typeAnnotations, typeAnnotation -> checkMethodTypeAnnotation(typeAnnotation, className, method));

		int syntheticParamCount = 0;

		if (method.parameters != null) {
			for (ParameterNode param : method.parameters) {
				if ((param.access & Opcodes.ACC_SYNTHETIC) != 0) {
					syntheticParamCount++;
				}
			}
		}

		for (var paramEntry : data.parameters().entrySet()) {
			int paramIndex = paramEntry.getKey();

			if (paramIndex < 0 || paramIndex >= Type.getArgumentCount(method.desc) - syntheticParamCount) {
				error("Invalid parameter index: {} for method: {}", paramIndex, errorKey.get());
				result = false;
				continue;
			}

			List<AnnotationNode> paramAnnotations = concatLists(
					method.visibleParameterAnnotations == null || paramIndex >= method.visibleParameterAnnotations.length ? null : method.visibleParameterAnnotations[paramIndex],
					method.invisibleParameterAnnotations == null || paramIndex >= method.invisibleParameterAnnotations.length ? null : method.invisibleParameterAnnotations[paramIndex]
			);
			result &= checkGenericData(() -> errorKey.get() + ":" + paramIndex, paramEntry.getValue(), paramAnnotations, List.of(), typeAnnotation -> true);

			if (!paramEntry.getValue().typeAnnotationsToRemove().isEmpty() || !paramEntry.getValue().typeAnnotationsToAdd().isEmpty()) {
				error("Type annotations cannot be added directly to method parameters: {}", errorKey.get());
				result = false;
			}
		}

		return result;
	}

	private boolean checkAnnotationsToRemove(Supplier<String> errorKey, Set<String> annotationsToRemove, List<AnnotationNode> annotations) {
		Set<String> annotationsNotRemoved = new LinkedHashSet<>(annotationsToRemove);

		for (AnnotationNode annotation : annotations) {
			annotationsNotRemoved.remove(Type.getType(annotation.desc).getInternalName());
		}

		if (annotationsNotRemoved.isEmpty()) {
			return true;
		}

		for (String annotation : annotationsNotRemoved) {
			error("Trying to remove annotation {} from {} but it's not present", annotation, errorKey.get());
		}

		return false;
	}

	private boolean checkAnnotationsToAdd(Supplier<String> errorKey, List<AnnotationNode> annotationsToAdd, List<AnnotationNode> annotations) {
		Set<String> existingAnnotations = new HashSet<>();

		for (AnnotationNode annotation : annotations) {
			existingAnnotations.add(annotation.desc);
		}

		boolean result = true;

		for (AnnotationNode annotation : annotationsToAdd) {
			if (!existingAnnotations.add(annotation.desc)) {
				error("Trying to add annotation {} to {} but it's already present", annotation.desc, errorKey.get());
				result = false;
			}

			result &= checkAnnotation(errorKey, annotation);
		}

		return result;
	}

	private boolean checkTypeAnnotationsToRemove(Supplier<String> errorKey, Set<TypeAnnotationKey> typeAnnotationsToRemove, List<TypeAnnotationNode> typeAnnotations) {
		Set<TypeAnnotationKey> typeAnnotationsNotRemoved = new LinkedHashSet<>(typeAnnotationsToRemove);

		for (TypeAnnotationNode typeAnnotation : typeAnnotations) {
			typeAnnotationsNotRemoved.remove(new TypeAnnotationKey(typeAnnotation.typeRef, typePathToString(typeAnnotation.typePath), Type.getType(typeAnnotation.desc).getInternalName()));
		}

		if (typeAnnotationsNotRemoved.isEmpty()) {
			return true;
		}

		for (TypeAnnotationKey typeAnnotation : typeAnnotationsNotRemoved) {
			error("Trying to remove type annotation {} (typeRef={}, typePath={}) from {} but it's not present", typeAnnotation.name(), typeAnnotation.typeRef(), typeAnnotation.typePath(), errorKey.get());
		}

		return false;
	}

	private boolean checkTypeAnnotationsToAdd(Supplier<String> errorKey, List<TypeAnnotationNode> typeAnnotationsToAdd, List<TypeAnnotationNode> typeAnnotations, TypeAnnotationChecker checker) {
		Set<TypeAnnotationKey> existingTypeAnnotations = new HashSet<>();

		for (TypeAnnotationNode typeAnnotation : typeAnnotations) {
			existingTypeAnnotations.add(new TypeAnnotationKey(typeAnnotation.typeRef, typePathToString(typeAnnotation.typePath), typeAnnotation.desc));
		}

		boolean result = true;

		for (TypeAnnotationNode typeAnnotation : typeAnnotationsToAdd) {
			if (!existingTypeAnnotations.add(new TypeAnnotationKey(typeAnnotation.typeRef, typePathToString(typeAnnotation.typePath), typeAnnotation.desc))) {
				error("Trying to add annotation {} (typeRef={}, typePath={}) to {} but it's already present", typeAnnotation.desc, typeAnnotation.typeRef, typeAnnotation.typePath, errorKey.get());
				result = false;
			}

			result &= checker.checkTypeAnnotation(typeAnnotation);
		}

		return result;
	}

	private boolean checkClassTypeAnnotation(TypeAnnotationNode typeAnnotation, ClassNode clazz) {
		if (!checkTypeRef(typeAnnotation.typeRef)) {
			return false;
		}

		TypeReference typeRef = new TypeReference(typeAnnotation.typeRef);
		TypePathCheckerVisitor typePathChecker = new TypePathCheckerVisitor(typeAnnotation.typePath);

		switch (typeRef.getSort()) {
		case TypeReference.CLASS_TYPE_PARAMETER -> {
			return checkTypeParameterTypeAnnotation("class", typeAnnotation, clazz.signature, typeRef.getTypeParameterIndex());
		}
		case TypeReference.CLASS_TYPE_PARAMETER_BOUND -> {
			if (!checkTypeParameterBoundTypeAnnotation("class", typeAnnotation, clazz.signature, typeRef.getTypeParameterIndex(), typeRef.getTypeParameterBoundIndex(), typePathChecker)) {
				return false;
			}
		}
		case TypeReference.CLASS_EXTENDS -> {
			int superTypeIndex = typeRef.getSuperTypeIndex();

			if (superTypeIndex == -1) {
				if (clazz.signature == null) {
					typePathChecker.visitClassType(Objects.requireNonNullElse(clazz.superName, "java/lang/Object"));
					typePathChecker.visitEnd();
				} else {
					new SignatureReader(clazz.signature).accept(new SignatureVisitor(Constants.ASM_VERSION) {
						@Override
						public SignatureVisitor visitSuperclass() {
							return typePathChecker;
						}
					});
				}
			} else {
				if (superTypeIndex >= clazz.interfaces.size()) {
					error("Invalid type reference for class type annotation: {}, interface index {} out of bounds", typeAnnotation.typeRef, superTypeIndex);
					return false;
				}

				if (clazz.signature == null) {
					typePathChecker.visitClassType(clazz.interfaces.get(superTypeIndex));
					typePathChecker.visitEnd();
				} else {
					new SignatureReader(clazz.signature).accept(new SignatureVisitor(Constants.ASM_VERSION) {
						int interfaceIndex = 0;

						@Override
						public SignatureVisitor visitInterface() {
							if (interfaceIndex++ == superTypeIndex) {
								return typePathChecker;
							} else {
								return this;
							}
						}
					});
				}
			}
		}
		default -> {
			error("Invalid type reference for class type annotation: {}", typeAnnotation.typeRef);
			return false;
		}
		}

		String typePathError = typePathChecker.getError();

		if (typePathError != null) {
			error("Invalid type path for class type annotation, typeRef: {}, error: {}", typeAnnotation.typeRef, typePathError);
			return false;
		}

		return true;
	}

	private boolean checkFieldTypeAnnotation(TypeAnnotationNode typeAnnotation, FieldNode field) {
		if (!checkTypeRef(typeAnnotation.typeRef)) {
			return false;
		}

		if (new TypeReference(typeAnnotation.typeRef).getSort() != TypeReference.FIELD) {
			error("Invalid type reference for field type annotation: {}", typeAnnotation.typeRef);
			return false;
		}

		String signature = Objects.requireNonNullElse(field.signature, field.desc);
		TypePathCheckerVisitor typePathChecker = new TypePathCheckerVisitor(typeAnnotation.typePath);
		new SignatureReader(signature).acceptType(typePathChecker);
		String typePathError = typePathChecker.getError();

		if (typePathError != null) {
			error("Invalid type path for field type annotation, typeRef: {}, error: {}", typeAnnotation.typeRef, typePathError);
			return false;
		}

		return true;
	}

	private boolean checkMethodTypeAnnotation(TypeAnnotationNode typeAnnotation, String className, MethodNode method) {
		if (!checkTypeRef(typeAnnotation.typeRef)) {
			return false;
		}

		TypeReference typeRef = new TypeReference(typeAnnotation.typeRef);
		TypePathCheckerVisitor typePathChecker = new TypePathCheckerVisitor(typeAnnotation.typePath);

		switch (typeRef.getSort()) {
		case TypeReference.METHOD_TYPE_PARAMETER -> {
			return checkTypeParameterTypeAnnotation("method", typeAnnotation, method.signature, typeRef.getTypeParameterIndex());
		}
		case TypeReference.METHOD_TYPE_PARAMETER_BOUND -> {
			if (!checkTypeParameterBoundTypeAnnotation("method", typeAnnotation, method.signature, typeRef.getTypeParameterIndex(), typeRef.getTypeParameterBoundIndex(), typePathChecker)) {
				return false;
			}
		}
		case TypeReference.METHOD_RETURN -> {
			if (method.signature == null) {
				new SignatureReader(Type.getReturnType(method.desc).getDescriptor()).acceptType(typePathChecker);
			} else {
				new SignatureReader(method.signature).accept(new SignatureVisitor(Constants.ASM_VERSION) {
					@Override
					public SignatureVisitor visitReturnType() {
						return typePathChecker;
					}
				});
			}
		}
		case TypeReference.METHOD_RECEIVER -> {
			if ((method.access & Opcodes.ACC_STATIC) != 0 || "<init>".equals(method.name)) {
				error("Invalid type reference for method type annotation: {}, method receiver used in a static context", typeAnnotation.typeRef);
				return false;
			}

			typePathChecker.visitClassType(className);
			typePathChecker.visitEnd();
		}
		case TypeReference.METHOD_FORMAL_PARAMETER -> {
			int formalParamIndex = typeRef.getFormalParameterIndex();

			if (method.signature == null) {
				int nonSyntheticParams = 0;
				boolean foundArgument = false;

				for (Type argumentType : Type.getArgumentTypes(method.desc)) {
					if ((method.access & Opcodes.ACC_SYNTHETIC) == 0) {
						if (nonSyntheticParams++ == formalParamIndex) {
							foundArgument = true;
							new SignatureReader(argumentType.getDescriptor()).acceptType(typePathChecker);
							break;
						}
					}
				}

				if (!foundArgument) {
					error("Invalid type reference for method type annotation: {}, formal parameter index {} out of bounds", typeAnnotation.typeRef, formalParamIndex);
					return false;
				}
			} else {
				var visitor = new SignatureVisitor(Constants.ASM_VERSION) {
					int paramIndex = 0;
					boolean found = false;

					@Override
					public SignatureVisitor visitParameterType() {
						if (paramIndex++ == formalParamIndex) {
							found = true;
							return typePathChecker;
						} else {
							return this;
						}
					}
				};
				new SignatureReader(method.signature).accept(visitor);

				if (!visitor.found) {
					error("Invalid type reference for method type annotation: {}, formal parameter index {} out of bounds", typeAnnotation.typeRef, formalParamIndex);
					return false;
				}
			}
		}
		case TypeReference.THROWS -> {
			int throwsIndex = typeRef.getExceptionIndex();

			if (method.signature == null) {
				if (method.exceptions == null || throwsIndex >= method.exceptions.size()) {
					error("Invalid type reference for method type annotation: {}, exception index {} out of bounds", typeAnnotation.typeRef, throwsIndex);
					return false;
				}

				typePathChecker.visitClassType(method.exceptions.get(throwsIndex));
				typePathChecker.visitEnd();
			} else {
				var visitor = new SignatureVisitor(Constants.ASM_VERSION) {
					int exceptionIndex = 0;
					boolean found = false;

					@Override
					public SignatureVisitor visitExceptionType() {
						if (exceptionIndex++ == throwsIndex) {
							found = true;
							return typePathChecker;
						} else {
							return this;
						}
					}
				};
				new SignatureReader(method.signature).accept(visitor);

				if (!visitor.found) {
					error("Invalid type reference for method type annotation: {}, exception index {} out of bounds", typeAnnotation.typeRef, throwsIndex);
					return false;
				}
			}
		}
		default -> {
			error("Invalid type reference for method type annotation: {}", typeAnnotation.typeRef);
			return false;
		}
		}

		String typePathError = typePathChecker.getError();

		if (typePathError != null) {
			error("Invalid type path for method type annotation, typeRef: {}, error: {}", typeAnnotation.typeRef, typePathError);
			return false;
		}

		return true;
	}

	private boolean checkTypeParameterTypeAnnotation(String memberType, TypeAnnotationNode typeAnnotation, @Nullable String signature, int typeParamIndex) {
		int formalParamCount;

		if (signature == null) {
			formalParamCount = 0;
		} else {
			var formalParamCounter = new SignatureVisitor(Constants.ASM_VERSION) {
				int count = 0;

				@Override
				public void visitFormalTypeParameter(String name) {
					count++;
				}
			};
			new SignatureReader(signature).accept(formalParamCounter);
			formalParamCount = formalParamCounter.count;
		}

		boolean result = true;

		if (typeParamIndex >= formalParamCount) {
			error("Invalid type reference for {} type annotation: {}, formal parameter index {} out of bounds", memberType, typeAnnotation.typeRef, typeParamIndex);
			result = false;
		}

		if (typeAnnotation.typePath != null && typeAnnotation.typePath.getLength() != 0) {
			error("Non-empty type path for annotation doesn't make sense for {}_TYPE_PARAMETER", memberType.toUpperCase(Locale.ROOT));
			result = false;
		}

		return result;
	}

	private boolean checkTypeParameterBoundTypeAnnotation(String memberType, TypeAnnotationNode typeAnnotation, @Nullable String signature, int typeParamIndex, int typeParamBoundIndex, TypePathCheckerVisitor typePathChecker) {
		var visitor = new SignatureVisitor(Constants.ASM_VERSION) {
					boolean found = false;
					int formalParamIndex = -1;
					int boundIndex = 0;

					@Override
					public void visitFormalTypeParameter(String name) {
						formalParamIndex++;
					}

					@Override
					public SignatureVisitor visitClassBound() {
						if (formalParamIndex == typeParamIndex) {
							if (boundIndex++ == typeParamBoundIndex) {
								found = true;
								return typePathChecker;
							}
						}

						return this;
					}

					@Override
					public SignatureVisitor visitInterfaceBound() {
						return visitClassBound();
					}
				};

		if (signature != null) {
			new SignatureReader(signature).accept(visitor);
		}

		if (!visitor.found) {
			error("Invalid type reference for {} type annotation: {}, formal parameter index {} bound index {} out of bounds", memberType, typeAnnotation.typeRef, typeParamIndex, typeParamBoundIndex);
			return false;
		}

		return true;
	}

	// copied from CheckClassAdapter
	private boolean checkTypeRef(int typeRef) {
		int mask = switch (typeRef >>> 24) {
		case TypeReference.CLASS_TYPE_PARAMETER, TypeReference.METHOD_TYPE_PARAMETER,
					TypeReference.METHOD_FORMAL_PARAMETER -> 0xFFFF0000;
		case TypeReference.FIELD, TypeReference.METHOD_RETURN, TypeReference.METHOD_RECEIVER,
					TypeReference.LOCAL_VARIABLE, TypeReference.RESOURCE_VARIABLE, TypeReference.INSTANCEOF,
					TypeReference.NEW, TypeReference.CONSTRUCTOR_REFERENCE, TypeReference.METHOD_REFERENCE -> 0xFF000000;
		case TypeReference.CLASS_EXTENDS, TypeReference.CLASS_TYPE_PARAMETER_BOUND,
					TypeReference.METHOD_TYPE_PARAMETER_BOUND, TypeReference.THROWS, TypeReference.EXCEPTION_PARAMETER -> 0xFFFFFF00;
		case TypeReference.CAST, TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT,
					TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT, TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT,
					TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> 0xFF0000FF;
		default -> 0;
		};

		if (mask == 0 || (typeRef & ~mask) != 0) {
			error("Invalid type reference {}", typeRef);
			return false;
		}

		return true;
	}

	private boolean checkAnnotation(Supplier<String> errorKey, AnnotationNode annotation) {
		if (!annotation.desc.startsWith("L") || !annotation.desc.endsWith(";")) {
			error("Invalid annotation descriptor: {}", annotation.desc);
			return false;
		}

		String internalName = annotation.desc.substring(1, annotation.desc.length() - 1);
		ClassNode annotationClass = getClass(internalName, true);

		if (annotationClass == null || (annotationClass.access & Opcodes.ACC_ANNOTATION) == 0) {
			error("No such annotation class: {}", internalName);
			return false;
		}

		Set<String> missingRequiredAttributes = new LinkedHashSet<>();
		Map<String, Type> attributeTypes = new HashMap<>();

		for (MethodNode method : annotationClass.methods) {
			if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
				attributeTypes.put(method.name, Type.getReturnType(method.desc));

				if (method.annotationDefault == null) {
					missingRequiredAttributes.add(method.name);
				}
			}
		}

		boolean result = true;

		if (annotation.values != null) {
			for (int i = 0; i < annotation.values.size(); i += 2) {
				String key = (String) annotation.values.get(i);
				Object value = annotation.values.get(i + 1);

				Type expectedType = attributeTypes.get(key);

				if (expectedType == null) {
					error("Unknown annotation attribute: {}.{}", internalName, key);
					result = false;
					continue;
				}

				result &= checkAnnotationValue(errorKey, key, value, expectedType);

				missingRequiredAttributes.remove(key);
			}
		}

		if (!missingRequiredAttributes.isEmpty()) {
			result = false;
			error("Annotation applied to {} is missing required attributes: {}", errorKey.get(), missingRequiredAttributes);
		}

		return result;
	}

	private boolean checkAnnotationValue(Supplier<String> errorKey, String name, Object value, Type expectedType) {
		if (expectedType.getSort() == Type.ARRAY) {
			if (!(value instanceof List<?> values)) {
				error("Annotation value is of type {}, expected array for attribute {}", getTypeName(value), name);
				return false;
			}

			boolean result = true;

			for (Object element : values) {
				result &= checkAnnotationValue(errorKey, name, element, expectedType.getElementType());
			}

			return result;
		}

		boolean result = true;

		boolean wrongType = switch (value) {
		case Boolean ignored -> expectedType.getSort() != Type.BOOLEAN;
		case Byte ignored -> expectedType.getSort() != Type.BYTE;
		case Character ignored -> expectedType.getSort() != Type.CHAR;
		case Short ignored -> expectedType.getSort() != Type.SHORT;
		case Integer ignored -> expectedType.getSort() != Type.INT;
		case Long ignored -> expectedType.getSort() != Type.LONG;
		case Float ignored -> expectedType.getSort() != Type.FLOAT;
		case Double ignored -> expectedType.getSort() != Type.DOUBLE;
		case String ignored -> !expectedType.getDescriptor().equals("Ljava/lang/String;");
		case Type ignored -> !expectedType.getDescriptor().equals("Ljava/lang/Class;");
		case String[] enumValue -> {
			if (!enumValue[0].startsWith("L") || !enumValue[0].endsWith(";")) {
				error("Invalid enum descriptor: {}", enumValue[0]);
				result = false;
				yield false;
			}

			boolean wrongEnumType = !expectedType.getDescriptor().equals(enumValue[0]);

			ClassNode enumClass = getClass(enumValue[0].substring(1, enumValue[0].length() - 1), true);

			if (enumClass == null) {
				error("No such enum class: {}", enumValue[0]);
				result = false;
				yield wrongEnumType;
			}

			if (!enumValueExists(enumClass, enumValue[1])) {
				error("Enum value {} does not exist in class {}", enumValue[1], enumValue[0]);
				result = false;
				yield wrongEnumType;
			}

			yield wrongEnumType;
		}
		case AnnotationNode annotation -> {
			result &= checkAnnotation(errorKey, annotation);
			yield !expectedType.getDescriptor().equals(annotation.desc);
		}
		case List<?> ignored -> true;
		default -> throw new AssertionError("Unexpected annotation value type: " + value.getClass().getName());
		};

		if (wrongType) {
			error("Annotation value is of type {}, expected {} for attribute {}", getTypeName(value), expectedType.getClassName(), name);
			result = false;
		}

		return result;
	}

	@Nullable
	private static FieldNode findField(ClassNode clazz, String nameAndDesc) {
		for (FieldNode field : clazz.fields) {
			if (nameAndDesc.equals(field.name + ":" + field.desc)) {
				return field;
			}
		}

		return null;
	}

	@Nullable
	private static MethodNode findMethod(ClassNode clazz, String nameAndDesc) {
		for (MethodNode method : clazz.methods) {
			if (nameAndDesc.equals(method.name + method.desc)) {
				return method;
			}
		}

		return null;
	}

	private static boolean enumValueExists(ClassNode enumClass, String name) {
		for (FieldNode field : enumClass.fields) {
			if (field.name.equals(name) && (field.access & Opcodes.ACC_ENUM) != 0) {
				return true;
			}
		}

		return false;
	}

	private static String getTypeName(Object value) {
		return switch (value) {
		case Boolean ignored -> "boolean";
		case Byte ignored -> "byte";
		case Character ignored -> "char";
		case Short ignored -> "short";
		case Integer ignored -> "int";
		case Long ignored -> "long";
		case Float ignored -> "float";
		case Double ignored -> "double";
		case String ignored -> "java.lang.String";
		case Type ignored -> "java.lang.Class";
		case String[] enumValue -> getSafeClassNameFromDesc(enumValue[0]);
		case AnnotationNode annotation -> getSafeClassNameFromDesc(annotation.desc);
		case List<?> ignored -> "array";
		default -> throw new AssertionError("Unexpected annotation value type: " + value.getClass().getName());
		};
	}

	private static String getSafeClassNameFromDesc(String desc) {
		return desc.startsWith("L") && desc.endsWith(";") ? desc.substring(1, desc.length() - 1).replace('/', '.') : desc;
	}

	private static String typePathToString(@Nullable TypePath typePath) {
		return typePath == null ? "" : typePath.toString();
	}

	private static <T> List<T> concatLists(@Nullable List<T> list1, @Nullable List<T> list2) {
		List<T> result = new ArrayList<>();

		if (list1 != null) {
			result.addAll(list1);
		}

		if (list2 != null) {
			result.addAll(list2);
		}

		return result;
	}

	@FunctionalInterface
	private interface TypeAnnotationChecker {
		boolean checkTypeAnnotation(TypeAnnotationNode typeAnnotation);
	}
}
