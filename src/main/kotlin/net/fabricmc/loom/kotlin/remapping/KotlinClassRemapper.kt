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

package net.fabricmc.loom.kotlin.remapping

import kotlinx.metadata.*
import kotlinx.metadata.internal.extensions.*
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import org.objectweb.asm.commons.Remapper

@OptIn(ExperimentalContextReceivers::class)
class KotlinClassRemapper(private val remapper: Remapper) {
    fun remap(clazz: KmClass): KmClass {
        clazz.name = remap(clazz.name)
        clazz.typeParameters.replaceAll(this::remap)
        clazz.supertypes.replaceAll(this::remap)
        clazz.functions.replaceAll(this::remap)
        clazz.properties.replaceAll(this::remap)
        clazz.typeAliases.replaceAll(this::remap)
        clazz.constructors.replaceAll(this::remap)
        clazz.nestedClasses.replaceAll(this::remap)
        clazz.sealedSubclasses.replaceAll(this::remap)
        clazz.contextReceiverTypes.replaceAll(this::remap)
        clazz.getExtensions().replaceAll(this::remap)
        return clazz
    }

    fun remap(lambda: KmLambda): KmLambda {
        lambda.function = remap(lambda.function)
        return lambda
    }

    fun remap(pkg: KmPackage): KmPackage {
        pkg.functions.replaceAll(this::remap)
        pkg.properties.replaceAll(this::remap)
        pkg.typeAliases.replaceAll(this::remap)
        pkg.getExtensions().replaceAll(this::remap)
        return pkg
    }

    private fun remap(name: ClassName): ClassName {
        return remapper.map(name)
    }

    private fun remap(type: KmType): KmType {
        type.classifier = when (val classifier = type.classifier) {
            is KmClassifier.Class -> KmClassifier.Class(remap(classifier.name))
            is KmClassifier.TypeParameter -> KmClassifier.TypeParameter(classifier.id)
            is KmClassifier.TypeAlias -> KmClassifier.TypeAlias(remap(classifier.name))
        }
        type.arguments.replaceAll(this::remap)
        type.abbreviatedType = type.abbreviatedType?.let { remap(it) }
        type.outerType = type.outerType?.let { remap(it) }
        type.flexibleTypeUpperBound = type.flexibleTypeUpperBound?.let { remap(it) }
        type.getExtensions().replaceAll(this::remap)
        return type
    }

    private fun remap(function: KmFunction): KmFunction {
        function.typeParameters.replaceAll(this::remap)
        function.receiverParameterType = function.receiverParameterType?.let { remap(it) }
        function.contextReceiverTypes.replaceAll(this::remap)
        function.valueParameters.replaceAll(this::remap)
        function.returnType = remap(function.returnType)
        function.getExtensions().replaceAll(this::remap)
        return function
    }

    private fun remap(property: KmProperty): KmProperty {
        property.typeParameters.replaceAll(this::remap)
        property.receiverParameterType = property.receiverParameterType?.let { remap(it) }
        property.contextReceiverTypes.replaceAll(this::remap)
        property.setterParameter = property.setterParameter?.let { remap(it) }
        property.returnType = remap(property.returnType)
        property.getExtensions().replaceAll(this::remap)
        return property
    }

    private fun remap(typeAlias: KmTypeAlias): KmTypeAlias {
        typeAlias.typeParameters.replaceAll(this::remap)
        typeAlias.underlyingType = remap(typeAlias.underlyingType)
        typeAlias.expandedType = remap(typeAlias.expandedType)
        typeAlias.annotations.replaceAll(this::remap)
        typeAlias.getExtensions().replaceAll(this::remap)
        return typeAlias
    }

    private fun remap(constructor: KmConstructor): KmConstructor {
        constructor.valueParameters.replaceAll(this::remap)
        constructor.getExtensions().replaceAll(this::remap)
        return constructor
    }

    private fun remap(typeParameter: KmTypeParameter): KmTypeParameter {
        typeParameter.upperBounds.replaceAll(this::remap)
        typeParameter.getExtensions().replaceAll(this::remap)
        return typeParameter
    }

    private fun remap(typeProjection: KmTypeProjection): KmTypeProjection {
        return KmTypeProjection(typeProjection.variance, typeProjection.type?.let { remap(it) })
    }

    private fun remap(flexibleTypeUpperBound: KmFlexibleTypeUpperBound): KmFlexibleTypeUpperBound {
        return KmFlexibleTypeUpperBound(remap(flexibleTypeUpperBound.type), flexibleTypeUpperBound.typeFlexibilityId)
    }

    private fun remap(valueParameter: KmValueParameter): KmValueParameter {
        valueParameter.type = remap(valueParameter.type)
        valueParameter.varargElementType = valueParameter.varargElementType?.let { remap(it) }
        valueParameter.getExtensions().replaceAll(this::remap)
        return valueParameter
    }

    private fun remap(annotation: KmAnnotation): KmAnnotation {
        return KmAnnotation(remap(annotation.className), annotation.arguments)
    }

    private fun remap(classExtension: KmClassExtension): KmClassExtension {
        JvmExtensionWrapper.Class.get(classExtension)?.let {
            it.localDelegatedProperties.replaceAll(this::remap)
            return it.extension
        }

        return classExtension
    }

    private fun remap(packageExtension: KmPackageExtension): KmPackageExtension {
        JvmExtensionWrapper.Package.get(packageExtension)?.let {
            it.localDelegatedProperties.replaceAll(this::remap)
            return it.extension
        }

        return packageExtension
    }

    private fun remap(typeExtension: KmTypeExtension): KmTypeExtension {
        JvmExtensionWrapper.Type.get(typeExtension)?.let {
            it.annotations.replaceAll(this::remap)
            return it.extension
        }

        return typeExtension
    }

    private fun remap(functionExtension: KmFunctionExtension): KmFunctionExtension {
        JvmExtensionWrapper.Function.get(functionExtension)?.let {
            it.signature = it.signature?.let { sig -> remap(sig) }
            return it.extension
        }

        return functionExtension
    }

    private fun remap(propertyExtension: KmPropertyExtension): KmPropertyExtension {
        JvmExtensionWrapper.Property.get(propertyExtension)?.let {
            it.fieldSignature = it.fieldSignature?.let { sig -> remap(sig) }
            it.getterSignature = it.getterSignature?.let { sig -> remap(sig) }
            it.setterSignature = it.setterSignature?.let { sig -> remap(sig) }
            it.syntheticMethodForAnnotations = it.syntheticMethodForAnnotations?.let { sig -> remap(sig) }
            it.syntheticMethodForDelegate = it.syntheticMethodForDelegate?.let { sig -> remap(sig) }
            return it.extension
        }

        return propertyExtension
    }

    private fun remap(typeAliasExtension: KmTypeAliasExtension): KmTypeAliasExtension {
        return typeAliasExtension
    }

    private fun remap(typeParameterExtension: KmTypeParameterExtension): KmTypeParameterExtension {
        return typeParameterExtension
    }

    private fun remap(valueParameterExtension: KmValueParameterExtension): KmValueParameterExtension {
        return valueParameterExtension
    }

    private fun remap(constructorExtension: KmConstructorExtension): KmConstructorExtension {
        JvmExtensionWrapper.Constructor.get(constructorExtension)?.let {
            it.signature = it.signature?.let { sig -> remap(sig) }
            return it.extension
        }

        return constructorExtension
    }

    private fun remap(signature: JvmMethodSignature): JvmMethodSignature {
        return JvmMethodSignature(signature.name, remapper.mapMethodDesc(signature.desc))
    }

    private fun remap(signature: JvmFieldSignature): JvmFieldSignature {
        return JvmFieldSignature(signature.name, remapper.mapDesc(signature.desc))
    }
}