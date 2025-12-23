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

package net.fabricmc.loom.test.unit

import java.nio.file.Path

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.task.tool.ValidateModProvidedJavadocTask
import net.fabricmc.loom.util.ZipUtils

class ValidateModProvidedJavadocTest extends Specification {
	@Shared
	@TempDir
	Path tempDir

	@Shared
	ValidateModProvidedJavadocTask.Validator validator

	def setupSpec() {
		ValidateModProvidedJavadocTask.ErrorReporter errorReporter = { problemId, currentPath, details, cause ->
			throw new IOException(details ?: problemId.displayName, cause)
		}

		def targetClass = JavadocTestTarget.class
		def classFilePath = targetClass.name.replace('.', '/') + ".class"
		def classBytes = targetClass.classLoader
				.getResourceAsStream(classFilePath)
				.withCloseable { it.readAllBytes() }
		def jarPath = tempDir.resolve('target.jar')
		ZipUtils.add(jarPath, classFilePath, classBytes)

		validator = new ValidateModProvidedJavadocTask.Validator(errorReporter, [jarPath.toFile()])
	}

	def cleanupSpec() {
		validator.close()
	}

	def "parsing error"() {
		setup:
		def mappingFile = tempDir.resolve('parsing_error.tiny')
		mappingFile.text = 'tny\t2\t0\tofficial\n'

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Cannot parse mappings')
	}

	def "incorrect namespace"() {
		setup:
		def mappingFile = tempDir.resolve('incorrect_namespace.tiny')
		mappingFile.text = 'tiny\t2\t0\tmagic\n'

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Expected official or source for the source namespace')
	}

	def "dst names"() {
		setup:
		def mappingFile = tempDir.resolve('dst_names.tiny')
		mappingFile.text = 'tiny\t2\t0\tofficial\tnamed\nc\tnet/fabricmc/loom/test/unit/JavadocTestTarget\tFoo\n'

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('These mappings cannot contain any destination names')
	}

	def "fallback source ns is valid"() {
		setup:
		def mappingFile = tempDir.resolve('fallback_src_ns_valid.tiny')
		mappingFile.text = 'tiny\t2\t0\tsource\n'

		when:
		validator.check(mappingFile, 'official')

		then:
		notThrown(IOException)
	}

	def "full valid example"() {
		setup:
		def mappingFile = tempDir.resolve('full_valid_example.tiny')
		mappingFile.text =
				'''\
				tiny\t2\t0\tofficial
				c\tnet/fabricmc/loom/test/unit/JavadocTestTarget
				\tc\tSome class docs.
				\tf\tLjava/lang/String;\tHELLO_WORLD
				\t\tc\tSome field docs.
				\tm\t(I)Ljava/lang/String;\tfoo
				\t\tc\tSome method docs.
				\t\tp\t0\tignored
				\t\t\tc\tSome param docs.
				'''.stripIndent()

		when:
		validator.check(mappingFile, 'official')

		then:
		notThrown(IOException)
	}

	def "missing class"() {
		setup:
		def mappingFile = tempDir.resolve('missing_class.tiny')
		mappingFile.text =
				'''\
				tiny\t2\t0\tofficial
				c\tUnknownClass
				\tc\tSome class docs.
				'''.stripIndent()

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Class UnknownClass does not exist')
	}

	def "missing field"() {
		setup:
		def mappingFile = tempDir.resolve('missing_field.tiny')
		mappingFile.text =
				'''\
				tiny\t2\t0\tofficial
				c\tnet/fabricmc/loom/test/unit/JavadocTestTarget
				\tf\tI\tUNKNOWN_FIELD
				\t\tc\tSome field docs.
				'''.stripIndent()

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Field net/fabricmc/loom/test/unit/JavadocTestTarget.UNKNOWN_FIELD:I does not exist')
	}

	def "missing method"() {
		setup:
		def mappingFile = tempDir.resolve('missing_method.tiny')
		mappingFile.text =
				'''\
				tiny\t2\t0\tofficial
				c\tnet/fabricmc/loom/test/unit/JavadocTestTarget
				\tm\t(I)V\tbar
				\t\tc\tSome method docs.
				'''.stripIndent()

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Method net/fabricmc/loom/test/unit/JavadocTestTarget.bar(I)V does not exist')
	}

	def "missing method for parameter docs"() {
		setup:
		def mappingFile = tempDir.resolve('missing_method_for_param.tiny')
		mappingFile.text =
				'''\
				tiny\t2\t0\tofficial
				c\tnet/fabricmc/loom/test/unit/JavadocTestTarget
				\tm\t(I)V\tbar
				\t\tp\t0\tignored
				\t\t\tc\tSome param docs.
				'''.stripIndent()

		when:
		validator.check(mappingFile, 'official')

		then:
		def e = thrown(IOException)
		e.message.contains('Method net/fabricmc/loom/test/unit/JavadocTestTarget.bar(I)V does not exist')
	}
}

class JavadocTestTarget {
	static final String HELLO_WORLD = 'Hello, world!'

	static String foo(int x) {
		return HELLO_WORLD.repeat(x)
	}
}
