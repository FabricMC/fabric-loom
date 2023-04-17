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

@file:Suppress("UNCHECKED_CAST")

package net.fabricmc.loom.kotlin.remapping

import kotlinx.metadata.*
import kotlinx.metadata.internal.extensions.*
import java.lang.reflect.Field
import kotlin.reflect.KClass

val KM_CLASS_EXTENSIONS = getField(KmClass::class)
val KM_PACKAGE_EXTENSIONS = getField(KmPackage::class)
val KM_TYPE_EXTENSIONS = getField(KmType::class)
val KM_FUNCTION_EXTENSIONS = getField(KmFunction::class)
val KM_PROPERTY_EXTENSIONS = getField(KmProperty::class)
val KM_TYPE_ALIAS_EXTENSIONS = getField(KmTypeAlias::class)
val KM_TYPE_PARAMETER_EXTENSIONS = getField(KmTypeParameter::class)
val KM_VALUE_PARAMETER_EXTENSIONS = getField(KmValueParameter::class)
val KM_CONSTRUCTOR_EXTENSIONS = getField(KmConstructor::class)

fun KmClass.getExtensions() : MutableList<KmClassExtension> {
    return KM_CLASS_EXTENSIONS.get(this) as MutableList<KmClassExtension>
}

fun KmPackage.getExtensions() : MutableList<KmPackageExtension> {
    return KM_PACKAGE_EXTENSIONS.get(this) as MutableList<KmPackageExtension>
}

fun KmType.getExtensions() : MutableList<KmTypeExtension> {
    return KM_TYPE_EXTENSIONS.get(this) as MutableList<KmTypeExtension>
}

fun KmFunction.getExtensions() : MutableList<KmFunctionExtension> {
    return KM_FUNCTION_EXTENSIONS.get(this) as MutableList<KmFunctionExtension>
}

fun KmProperty.getExtensions() : MutableList<KmPropertyExtension> {
    return KM_PROPERTY_EXTENSIONS.get(this) as MutableList<KmPropertyExtension>
}

fun KmTypeAlias.getExtensions() : MutableList<KmTypeAliasExtension> {
    return KM_TYPE_ALIAS_EXTENSIONS.get(this) as MutableList<KmTypeAliasExtension>
}

fun KmTypeParameter.getExtensions() : MutableList<KmTypeParameterExtension> {
    return KM_TYPE_PARAMETER_EXTENSIONS.get(this) as MutableList<KmTypeParameterExtension>
}

fun KmValueParameter.getExtensions() : MutableList<KmValueParameterExtension> {
    return KM_VALUE_PARAMETER_EXTENSIONS.get(this) as MutableList<KmValueParameterExtension>
}

fun KmConstructor.getExtensions() : MutableList<KmConstructorExtension> {
    return KM_CONSTRUCTOR_EXTENSIONS.get(this) as MutableList<KmConstructorExtension>
}

private fun getField(clazz: KClass<*>): Field {
    val field = clazz.java.getDeclaredField("extensions")
    field.isAccessible = true
    return field
}