/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmClassExtensionVisitor
import kotlinx.metadata.KmClassVisitor
import kotlinx.metadata.KmConstructorExtensionVisitor
import kotlinx.metadata.KmConstructorVisitor
import kotlinx.metadata.KmContractVisitor
import kotlinx.metadata.KmEffectExpressionVisitor
import kotlinx.metadata.KmEffectInvocationKind
import kotlinx.metadata.KmEffectType
import kotlinx.metadata.KmEffectVisitor
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmFunctionExtensionVisitor
import kotlinx.metadata.KmFunctionVisitor
import kotlinx.metadata.KmLambdaVisitor
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeAliasVisitor
import kotlinx.metadata.KmTypeExtensionVisitor
import kotlinx.metadata.KmTypeParameterExtensionVisitor
import kotlinx.metadata.KmTypeParameterVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmValueParameterVisitor
import kotlinx.metadata.KmVariance
import kotlinx.metadata.jvm.JvmClassExtensionVisitor
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.JvmTypeExtensionVisitor
import kotlinx.metadata.jvm.JvmTypeParameterExtensionVisitor
import org.objectweb.asm.commons.Remapper

class RemappingKmVisitors(private val remapper: Remapper) {
    private fun remapJvmMethodSignature(signature: JvmMethodSignature?): JvmMethodSignature? {
        if (signature != null) {
            return JvmMethodSignature(signature.name, remapper.mapMethodDesc(signature.desc))
        }

        return null
    }

    private fun remapJvmFieldSignature(signature: JvmFieldSignature?): JvmFieldSignature? {
        if (signature != null) {
            return JvmFieldSignature(signature.name, remapper.mapDesc(signature.desc))
        }

        return null
    }

    inner class RemappingKmClassVisitor(delegate: KmClassVisitor?) : KmClassVisitor(delegate) {
        override fun visit(flags: Flags, name: ClassName) {
            super.visit(flags, remapper.map(name))
        }

        override fun visitNestedClass(name: String) {
            super.visitNestedClass(remapper.map(name))
        }

        override fun visitSealedSubclass(name: ClassName) {
            super.visitSealedSubclass(remapper.map(name))
        }

        override fun visitSupertype(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitSupertype(flags))
        }

        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor {
            return RemappingKmFunctionVisitor(super.visitFunction(flags, name))
        }

        override fun visitInlineClassUnderlyingType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitInlineClassUnderlyingType(flags))
        }

        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor {
            return RemappingKmPropertyVisitor(super.visitProperty(flags, name, getterFlags, setterFlags))
        }

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor {
            return RemappingKmTypeAliasVisitor(super.visitTypeAlias(flags, name))
        }

        override fun visitConstructor(flags: Flags): KmConstructorVisitor {
            return RemappingKmConstructorVisitor(super.visitConstructor(flags))
        }

        override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor {
            return RemappingJvmClassExtensionVisitor(super.visitExtensions(type) as JvmClassExtensionVisitor?)
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor {
            return RemappingKmTypeParameterVisitor(super.visitTypeParameter(flags, name, id, variance))
        }
    }

    inner class RemappingKmLambdaVisitor(delegate: KmLambdaVisitor?) : KmLambdaVisitor(delegate) {
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor {
            return RemappingKmFunctionVisitor(super.visitFunction(flags, name))
        }
    }

    inner class RemappingKmTypeVisitor(delegate: KmTypeVisitor?) : KmTypeVisitor(delegate) {
        override fun visitClass(name: ClassName) {
            super.visitClass(remapper.map(name))
        }

        override fun visitTypeAlias(name: ClassName) {
            super.visitTypeAlias(remapper.map(name))
        }

        override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitAbbreviatedType(flags))
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitArgument(flags, variance))
        }

        override fun visitOuterType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitOuterType(flags))
        }

        override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitFlexibleTypeUpperBound(flags, typeFlexibilityId))
        }

        override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor {
            return RemappingJvmTypeExtensionVisitor(super.visitExtensions(type) as JvmTypeExtensionVisitor?)
        }
    }

    inner class RemappingJvmTypeExtensionVisitor(delegate: JvmTypeExtensionVisitor?) : JvmTypeExtensionVisitor(delegate) {
        override fun visitAnnotation(annotation: KmAnnotation) {
            super.visitAnnotation(KmAnnotation(remapper.map(annotation.className), annotation.arguments))
        }
    }

    inner class RemappingKmFunctionVisitor(delegate: KmFunctionVisitor?) : KmFunctionVisitor(delegate) {
        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReceiverParameterType(flags))
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReturnType(flags))
        }

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor {
            return RemappingKmValueParameterVisitor(super.visitValueParameter(flags, name))
        }

        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor {
            return RemappingJvmFunctionExtensionVisitor(super.visitExtensions(type) as JvmFunctionExtensionVisitor)
        }

        override fun visitContract(): KmContractVisitor {
            return RemappingKmContractVisitor(super.visitContract())
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor {
            return RemappingKmTypeParameterVisitor(super.visitTypeParameter(flags, name, id, variance))
        }
    }

    inner class RemappingKmContractVisitor(delegate: KmContractVisitor?) : KmContractVisitor(delegate) {
        override fun visitEffect(type: KmEffectType, invocationKind: KmEffectInvocationKind?): KmEffectVisitor {
            return RemappingKmEffectVisitor(super.visitEffect(type, invocationKind))
        }
    }

    inner class RemappingKmEffectVisitor(delegate: KmEffectVisitor?) : KmEffectVisitor(delegate) {
        override fun visitConclusionOfConditionalEffect(): KmEffectExpressionVisitor {
            return RemappingKmEffectExpressionVisitor(super.visitConclusionOfConditionalEffect())
        }

        override fun visitConstructorArgument(): KmEffectExpressionVisitor {
            return RemappingKmEffectExpressionVisitor(super.visitConclusionOfConditionalEffect())
        }
    }

    inner class RemappingKmEffectExpressionVisitor(delegate: KmEffectExpressionVisitor?) : KmEffectExpressionVisitor(delegate) {
        override fun visitAndArgument(): KmEffectExpressionVisitor {
            return RemappingKmEffectExpressionVisitor(super.visitAndArgument())
        }

        override fun visitIsInstanceType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitIsInstanceType(flags))
        }

        override fun visitOrArgument(): KmEffectExpressionVisitor {
            return RemappingKmEffectExpressionVisitor(super.visitOrArgument())
        }
    }

    inner class RemappingKmPropertyVisitor(delegate: KmPropertyVisitor?) : KmPropertyVisitor(delegate) {
        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReceiverParameterType(flags))
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReturnType(flags))
        }

        override fun visitSetterParameter(flags: Flags, name: String): KmValueParameterVisitor {
            return RemappingKmValueParameterVisitor(super.visitSetterParameter(flags, name))
        }

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor {
            return RemappingJvmPropertyExtensionVisitor(super.visitExtensions(type) as JvmPropertyExtensionVisitor?)
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor {
            return RemappingKmTypeParameterVisitor(super.visitTypeParameter(flags, name, id, variance))
        }
    }

    inner class RemappingJvmPropertyExtensionVisitor(delegate: JvmPropertyExtensionVisitor?) : JvmPropertyExtensionVisitor(delegate) {
        override fun visit(
            jvmFlags: Flags,
            fieldSignature: JvmFieldSignature?,
            getterSignature: JvmMethodSignature?,
            setterSignature: JvmMethodSignature?
        ) {
            super.visit(jvmFlags, remapJvmFieldSignature(fieldSignature), remapJvmMethodSignature(getterSignature), remapJvmMethodSignature(setterSignature))
        }

        override fun visitSyntheticMethodForAnnotations(signature: JvmMethodSignature?) {
            super.visitSyntheticMethodForAnnotations(remapJvmMethodSignature(signature))
        }

        override fun visitSyntheticMethodForDelegate(signature: JvmMethodSignature?) {
            super.visitSyntheticMethodForDelegate(remapJvmMethodSignature(signature))
        }
    }

    inner class RemappingKmTypeParameterVisitor(delegate: KmTypeParameterVisitor?) : KmTypeParameterVisitor(delegate) {
        override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor {
            return RemappingJvmTypeParameterExtensionVisitor(super.visitExtensions(type) as JvmTypeParameterExtensionVisitor?)
        }

        override fun visitUpperBound(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitUpperBound(flags))
        }
    }

    inner class RemappingJvmTypeParameterExtensionVisitor(delegate: JvmTypeParameterExtensionVisitor?) : JvmTypeParameterExtensionVisitor(delegate) {
        override fun visitAnnotation(annotation: KmAnnotation) {
            super.visitAnnotation(KmAnnotation(remapper.map(annotation.className), annotation.arguments))
        }
    }

    inner class RemappingKmValueParameterVisitor(delegate: KmValueParameterVisitor?) : KmValueParameterVisitor(delegate) {
        override fun visitType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitType(flags))
        }

        override fun visitVarargElementType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitVarargElementType(flags))
        }
    }

    inner class RemappingKmTypeAliasVisitor(delegate: KmTypeAliasVisitor?) : KmTypeAliasVisitor(delegate) {
        override fun visitExpandedType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitExpandedType(flags))
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor {
            return RemappingKmTypeParameterVisitor(super.visitTypeParameter(flags, name, id, variance))
        }

        override fun visitUnderlyingType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitUnderlyingType(flags))
        }

        override fun visitAnnotation(annotation: KmAnnotation) {
            super.visitAnnotation(KmAnnotation(remapper.map(annotation.className), annotation.arguments))
        }
    }

    inner class RemappingJvmFunctionExtensionVisitor(delegate: JvmFunctionExtensionVisitor?) : JvmFunctionExtensionVisitor(delegate) {
        override fun visit(signature: JvmMethodSignature?) {
            super.visit(remapJvmMethodSignature(signature))
        }

        override fun visitLambdaClassOriginName(internalName: String) {
            super.visitLambdaClassOriginName(remapper.map(internalName))
        }
    }

    inner class RemappingJvmClassExtensionVisitor(delegate: JvmClassExtensionVisitor?) : JvmClassExtensionVisitor(delegate) {
        override fun visitAnonymousObjectOriginName(internalName: String) {
            super.visitAnonymousObjectOriginName(remapper.map(internalName))
        }

        override fun visitLocalDelegatedProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor {
            return RemappingKmPropertyVisitor(super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags))
        }
    }

    inner class RemappingKmConstructorVisitor(delegate: KmConstructorVisitor?) : KmConstructorVisitor(delegate) {
        override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor {
            return RemappingJvmConstructorExtensionVisitor(super.visitExtensions(type) as JvmConstructorExtensionVisitor?)
        }

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor {
            return RemappingKmValueParameterVisitor(super.visitValueParameter(flags, name))
        }
    }

    inner class RemappingJvmConstructorExtensionVisitor(delegate: JvmConstructorExtensionVisitor?) : JvmConstructorExtensionVisitor(delegate) {
        override fun visit(signature: JvmMethodSignature?) {
            super.visit(remapJvmMethodSignature(signature))
        }
    }

    inner class RemappingKmPackageVisitor(delegate: KmPackageVisitor?) : KmPackageVisitor(delegate) {
        override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor {
            return RemappingJvmPackageExtensionVisitor(super.visitExtensions(type) as JvmPackageExtensionVisitor?)
        }

        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor {
            return RemappingKmFunctionVisitor(super.visitFunction(flags, name))
        }

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor {
            return RemappingKmPropertyVisitor(super.visitProperty(flags, name, getterFlags, setterFlags))
        }

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor {
            return RemappingKmTypeAliasVisitor(super.visitTypeAlias(flags, name))
        }
    }

    inner class RemappingJvmPackageExtensionVisitor(delegate: JvmPackageExtensionVisitor?) : JvmPackageExtensionVisitor(delegate) {
        override fun visitLocalDelegatedProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor {
            return RemappingKmPropertyVisitor(super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags))
        }
    }
}
