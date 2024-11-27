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

import org.objectweb.asm.commons.Remapper
import kotlin.metadata.ClassName
import kotlin.metadata.ExperimentalContextReceivers
import kotlin.metadata.KmAnnotation
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmConstructor
import kotlin.metadata.KmFlexibleTypeUpperBound
import kotlin.metadata.KmFunction
import kotlin.metadata.KmLambda
import kotlin.metadata.KmPackage
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeAlias
import kotlin.metadata.KmTypeParameter
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.KmValueParameter
import kotlin.metadata.isLocalClassName
import kotlin.metadata.jvm.JvmFieldSignature
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.annotations
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.localDelegatedProperties
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.jvm.syntheticMethodForAnnotations
import kotlin.metadata.jvm.syntheticMethodForDelegate
import kotlin.metadata.jvm.toJvmInternalName

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
        clazz.localDelegatedProperties.replaceAll(this::remap)
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
        pkg.localDelegatedProperties.replaceAll(this::remap)
        return pkg
    }

    private fun remap(name: ClassName): ClassName {
        val local = name.isLocalClassName()
        val remapped = remapper.map(name.toJvmInternalName()).replace('$', '.')

        if (local) {
            return ".$remapped"
        }

        return remapped
    }

    private fun remap(type: KmType): KmType {
        type.classifier =
            when (val classifier = type.classifier) {
                is KmClassifier.Class -> KmClassifier.Class(remap(classifier.name))
                is KmClassifier.TypeParameter -> KmClassifier.TypeParameter(classifier.id)
                is KmClassifier.TypeAlias -> KmClassifier.TypeAlias(remap(classifier.name))
            }
        type.arguments.replaceAll(this::remap)
        type.abbreviatedType = type.abbreviatedType?.let { remap(it) }
        type.outerType = type.outerType?.let { remap(it) }
        type.flexibleTypeUpperBound = type.flexibleTypeUpperBound?.let { remap(it) }
        type.annotations.replaceAll(this::remap)
        return type
    }

    private fun remap(function: KmFunction): KmFunction {
        function.typeParameters.replaceAll(this::remap)
        function.receiverParameterType = function.receiverParameterType?.let { remap(it) }
        function.contextReceiverTypes.replaceAll(this::remap)
        function.valueParameters.replaceAll(this::remap)
        function.returnType = remap(function.returnType)
        function.signature = function.signature?.let { remap(it) }
        return function
    }

    private fun remap(property: KmProperty): KmProperty {
        property.typeParameters.replaceAll(this::remap)
        property.receiverParameterType = property.receiverParameterType?.let { remap(it) }
        property.contextReceiverTypes.replaceAll(this::remap)
        property.setterParameter = property.setterParameter?.let { remap(it) }
        property.returnType = remap(property.returnType)
        property.fieldSignature = property.fieldSignature?.let { remap(it) }
        property.getterSignature = property.getterSignature?.let { remap(it) }
        property.setterSignature = property.setterSignature?.let { remap(it) }
        property.syntheticMethodForAnnotations = property.syntheticMethodForAnnotations?.let { remap(it) }
        property.syntheticMethodForDelegate = property.syntheticMethodForDelegate?.let { remap(it) }
        return property
    }

    private fun remap(typeAlias: KmTypeAlias): KmTypeAlias {
        typeAlias.typeParameters.replaceAll(this::remap)
        typeAlias.underlyingType = remap(typeAlias.underlyingType)
        typeAlias.expandedType = remap(typeAlias.expandedType)
        typeAlias.annotations.replaceAll(this::remap)
        return typeAlias
    }

    private fun remap(constructor: KmConstructor): KmConstructor {
        constructor.valueParameters.replaceAll(this::remap)
        constructor.signature = constructor.signature?.let { remap(it) }
        return constructor
    }

    private fun remap(typeParameter: KmTypeParameter): KmTypeParameter {
        typeParameter.upperBounds.replaceAll(this::remap)
        typeParameter.annotations.replaceAll(this::remap)
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
        return valueParameter
    }

    private fun remap(annotation: KmAnnotation): KmAnnotation {
        return KmAnnotation(remap(annotation.className), annotation.arguments)
    }

    private fun remap(signature: JvmMethodSignature): JvmMethodSignature {
        return JvmMethodSignature(signature.name, remapper.mapMethodDesc(signature.descriptor))
    }

    private fun remap(signature: JvmFieldSignature): JvmFieldSignature {
        return JvmFieldSignature(signature.name, remapper.mapDesc(signature.descriptor))
    }
}
