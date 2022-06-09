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

import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AnnotationNode
import org.slf4j.LoggerFactory

class KotlinClassMetadataRemappingAnnotationVisitor(private val remapper: Remapper, val next: AnnotationVisitor, val className: String?) :
    AnnotationNode(Opcodes.ASM9, KotlinMetadataRemappingClassVisitor.ANNOTATION_DESCRIPTOR) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var _name: String? = null

    override fun visit(name: String?, value: Any?) {
        super.visit(name, value)
        this._name = name
    }

    override fun visitEnd() {
        super.visitEnd()

        val header = readHeader() ?: return

        val headerVersion = KotlinVersion(header.metadataVersion[0], header.metadataVersion[1], 0)
        val currentMinorVersion = KotlinVersion(KotlinVersion.CURRENT.major, KotlinVersion.CURRENT.minor, 0)

        if (headerVersion != currentMinorVersion) {
            logger.info("Kotlin metadata for class ($className) as it was built using a different major Kotlin version (${header.metadataVersion[0]}.${header.metadataVersion[1]}.x) while the remapper is using (${KotlinVersion.CURRENT}).")
        }

        when (val metadata = KotlinClassMetadata.read(header)) {
            is KotlinClassMetadata.Class -> {
                val klass = metadata.toKmClass()
                val writer = KotlinClassMetadata.Class.Writer()
                klass.accept(RemappingKmVisitors(remapper).RemappingKmClassVisitor(writer))
                val remapped = writer.write(header.metadataVersion, header.extraInt).header
                writeClassHeader(remapped)
                validateKotlinClassHeader(remapped, header)
            }
            is KotlinClassMetadata.SyntheticClass -> {
                val klambda = metadata.toKmLambda()

                if (klambda != null) {
                    val writer = KotlinClassMetadata.SyntheticClass.Writer()
                    klambda.accept(RemappingKmVisitors(remapper).RemappingKmLambdaVisitor(writer))
                    val remapped = writer.write(header.metadataVersion, header.extraInt).header
                    writeClassHeader(remapped)
                    validateKotlinClassHeader(remapped, header)
                } else {
                    accept(next)
                }
            }
            is KotlinClassMetadata.FileFacade -> {
                val kpackage = metadata.toKmPackage()
                val writer = KotlinClassMetadata.FileFacade.Writer()
                kpackage.accept(RemappingKmVisitors(remapper).RemappingKmPackageVisitor(writer))
                val remapped = writer.write(header.metadataVersion, header.extraInt).header
                writeClassHeader(remapped)
                validateKotlinClassHeader(remapped, header)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val kpackage = metadata.toKmPackage()
                val writer = KotlinClassMetadata.MultiFileClassPart.Writer()
                kpackage.accept(RemappingKmVisitors(remapper).RemappingKmPackageVisitor(writer))
                val remapped = writer.write(metadata.facadeClassName, metadata.header.metadataVersion, metadata.header.extraInt).header
                writeClassHeader(remapped)
                validateKotlinClassHeader(remapped, header)
            }
            is KotlinClassMetadata.MultiFileClassFacade, is KotlinClassMetadata.Unknown, null -> {
                // do nothing
                accept(next)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readHeader(): KotlinClassHeader? {
        var kind: Int? = null
        var metadataVersion: IntArray? = null
        var data1: Array<String>? = null
        var data2: Array<String>? = null
        var extraString: String? = null
        var packageName: String? = null
        var extraInt: Int? = null

        if (values == null) {
            return null
        }

        values.chunked(2).forEach { (name, value) ->
            when (name) {
                "k" -> kind = value as Int
                "mv" -> metadataVersion = (value as List<Int>).toIntArray()
                "d1" -> data1 = (value as List<String>).toTypedArray()
                "d2" -> data2 = (value as List<String>).toTypedArray()
                "xs" -> extraString = value as String
                "pn" -> packageName = value as String
                "xi" -> extraInt = value as Int
            }
        }

        return KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }

    private fun writeClassHeader(header: KotlinClassHeader) {
        val newNode = AnnotationNode(api, desc)
        newNode.values = this.values.toMutableList()

        newNode.run {
            for (i in values.indices step 2) {
                when (values[i]) {
                    "k" -> values[i + 1] = header.kind
                    "mv" -> values[i + 1] = header.metadataVersion.toList()
                    "d1" -> values[i + 1] = header.data1.toList()
                    "d2" -> values[i + 1] = header.data2.toList()
                    "xs" -> values[i + 1] = header.extraString
                    "pn" -> values[i + 1] = header.packageName
                    "xi" -> values[i + 1] = header.extraInt
                }
            }
        }

        newNode.accept(next)
    }

    private fun validateKotlinClassHeader(remapped: KotlinClassHeader, original: KotlinClassHeader) {
        // This can happen when the remapper is ran on a kotlin version that does not match the version the class was compiled with.
        if (remapped.data2.size != original.data2.size) {
            logger.info("Kotlin class metadata size mismatch: data2 size does not match original in class $className. New: ${remapped.data2.size} Old: ${original.data2.size}")
        }
    }
}
