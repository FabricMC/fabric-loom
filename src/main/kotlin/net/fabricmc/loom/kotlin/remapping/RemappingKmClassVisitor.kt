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
import kotlinx.metadata.KmClassVisitor
import kotlinx.metadata.KmFunctionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeAliasVisitor
import kotlinx.metadata.KmTypeParameterVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmValueParameterVisitor
import kotlinx.metadata.KmVariance
import org.objectweb.asm.commons.Remapper

class RemappingKmClassVisitor(private val remapper: Remapper, delegate: KmClassVisitor?) : KmClassVisitor(delegate) {
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
    }

    inner class RemappingKmFunctionVisitor(delegate: KmFunctionVisitor?) : KmFunctionVisitor(delegate) {
        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReceiverParameterType(flags))
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor {
            return RemappingKmTypeVisitor(super.visitReturnType(flags))
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? {
            // TODO need the desc somehow?
            return super.visitTypeParameter(flags, name, id, variance)
        }

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? {
            // TODO need the desc somehow to remap the name?
            return super.visitValueParameter(flags, name)
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
        override fun visitExpandedType(flags: Flags): KmTypeVisitor? {
            return super.visitExpandedType(flags)
        }

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? {
            // TODO need the desc somehow to remap the name?
            return super.visitTypeParameter(flags, name, id, variance)
        }

        override fun visitUnderlyingType(flags: Flags): KmTypeVisitor? {
            return RemappingKmTypeVisitor(super.visitUnderlyingType(flags))
        }
    }
}
