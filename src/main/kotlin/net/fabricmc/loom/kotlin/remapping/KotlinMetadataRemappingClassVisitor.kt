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

import org.jetbrains.annotations.VisibleForTesting
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper

class KotlinMetadataRemappingClassVisitor(
    private val remapper: Remapper,
    next: ClassVisitor?,
) : ClassVisitor(Opcodes.ASM9, next) {
    companion object {
        val ANNOTATION_DESCRIPTOR: String = Type.getDescriptor(Metadata::class.java)
        private val KOTLIN_PROPERTY_REFERENCE_IMPLS =
            setOf(
                "kotlin/jvm/internal/PropertyReference0Impl",
                "kotlin/jvm/internal/PropertyReference1Impl",
                "kotlin/jvm/internal/PropertyReference2Impl",
                "kotlin/jvm/internal/MutablePropertyReference0Impl",
                "kotlin/jvm/internal/MutablePropertyReference1Impl",
                "kotlin/jvm/internal/MutablePropertyReference2Impl",
            )
    }

    var className: String? = null
    private var propertyReferenceImpl = false

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        this.className = name
        this.propertyReferenceImpl = superName in KOTLIN_PROPERTY_REFERENCE_IMPLS
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        var result: AnnotationVisitor? = super.visitAnnotation(descriptor, visible)

        if (descriptor == ANNOTATION_DESCRIPTOR && result != null) {
            try {
                result = KotlinClassMetadataRemappingAnnotationVisitor(remapper, result, className)
            } catch (e: Exception) {
                throw RuntimeException("Failed to remap Kotlin metadata annotation in class $className", e)
            }
        }

        return result
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions)

        if (!propertyReferenceImpl || name != "<init>") {
            return next
        }

        return object : MethodVisitor(api, next) {
            override fun visitLdcInsn(value: Any?) {
                if (value is String) {
                    remapPropertyReferenceSignature(value)?.let {
                        super.visitLdcInsn(it)
                        return
                    }
                }

                super.visitLdcInsn(value)
            }
        }
    }

    @VisibleForTesting
    fun getRuntimeKotlinVersion(): String = KotlinVersion.CURRENT.toString()

    private fun remapPropertyReferenceSignature(signature: String): String? {
        val descriptorStart = signature.indexOf('(')

        if (descriptorStart <= 0) {
            return null
        }

        val methodDescriptor = signature.substring(descriptorStart)

        return try {
            Type.getMethodType(methodDescriptor)
            signature.substring(0, descriptorStart) + remapper.mapMethodDesc(methodDescriptor)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
