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

package net.fabricmc.loom.test.util

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.fabricmc.loom.util.FileSystemUtil
import net.fabricmc.tinyremapper.FileSystemReference
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

import java.nio.file.FileSystem
import java.nio.file.Files

trait FileSystemTestTrait {
	FileSystem fileSystem = Jimfs.newFileSystem(Configuration.forCurrentPlatform())
	FileSystemUtil.Delegate delegate

	private FileSystemReference reference

	@SuppressWarnings('GroovyAccessibility')
	def setup() {
		synchronized (FileSystemReference.openFsMap) {
			reference = new FileSystemReference(fileSystem)
			FileSystemReference.openFsMap.put(fileSystem, Collections.emptySet())
		}

		delegate = new FileSystemUtil.Delegate(reference)
	}

	def cleanup() {
		fileSystem.close()
	}

	// Copy a class to the fs and return the path
	String copyClass(Class<?> clazz) {
		def classname = clazz.name.replace('.', '/')
		def filename = "${classname}.class"
		def input = clazz.classLoader.getResourceAsStream(filename)

		if (input == null) {
			throw new NullPointerException("Unable to find $filename")
		}

		def path = delegate.getPath(filename)

		Files.createDirectories(path.getParent())
		Files.copy(input, path)

		return classname
	}

	ClassNode readClass(String classname) {
		def filename = "${classname}.class"
		def path = delegate.getPath(filename)
		def bytes = Files.readAllBytes(path)

		def classNode = new ClassNode()
		def classReader = new ClassReader(bytes)
		classReader.accept(classNode, 0)

		return classNode
	}
}