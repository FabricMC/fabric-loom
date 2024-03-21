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
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper

class KotlinMetadataRemappingClassVisitor(private val remapper: Remapper, next: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, next) {
    companion object {
        val ANNOTATION_DESCRIPTOR: String = Type.getDescriptor(Metadata::class.java)
    }

    var className: String? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        this.className = name
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

    @VisibleForTesting
    fun getRuntimeKotlinVersion(): String {
        return KotlinVersion.CURRENT.toString()
    }
}
