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

package net.fabricmc.loom.test.unit.processor

import org.jspecify.annotations.Nullable
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import spock.lang.Specification

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.validate.TypePathCheckerVisitor

class TypePathCheckerVisitorTest extends Specification {
	def "empty / null TypePath targets the whole type"() {
		expect:
		validate(null, "Ljava/lang/String;") == null
		validate("", "Ljava/lang/String;") == null
	}

	def "array element steps validate correctly"() {
		expect:
		validate("[", "[Ljava/lang/String;") == null
		validate("[[", "[Ljava/lang/String;") == "TypePath not fully consumed at index 1, remaining steps: '['"
	}

	def "type argument indexing and bounds"() {
		expect:
		// List<String>
		validate("0;", "Ljava/util/List<Ljava/lang/String;>;") == null
		// second arg doesn't exist
		validate("1;", "Ljava/util/List<Ljava/lang/String;>;") == "TypePath not fully consumed at index 0, remaining steps: '1;'"

		// List<?> -> unbounded wildcard is a valid terminal target for type-argument 0
		validate("0;", "Ljava/util/List<*>;") == null
		// but attempting to follow with a wildcard-bound step (0;*) is invalid for an unbounded wildcard
		validate("0;*", "Ljava/util/List<*>;") == "TypePath targets unbounded wildcard '*' at step 0 but contains further steps; '*' is terminal."

		// List<? extends Number> -> wildcard bound (*) after selecting arg 0 is valid
		validate("0;*", "Ljava/util/List<+Ljava/lang/Number;>;") == null
	}

	def "inner class stepping"() {
		expect:
		// Simple inner class descriptor
		validate(".", "Lcom/example/Outer\$Inner;") == null

		// Wrong step (trying to use array step on a non-array)
		validate("[", "Lcom/example/Outer\$Inner;") == "At step 0 expected inner type '.' but found '['."
	}

	def "type variable cannot be followed by extra steps"() {
		expect:
		validate("0;", "TMyType;") == "TypePath has extra steps starting at index 0 ('0;') but reached type variable 'MyType'."
	}

	def "complex: nested generics and bounds"() {
		expect:
		// Map<String, List<? extends Number>>
		String sig = "Ljava/util/Map<Ljava/lang/String;Ljava/util/List<+Ljava/lang/Number;>;>;"
		// target Map value type argument (index 1) and then the List's type argument (index 0) and its bound:
		// path: 1;0;*  -> TYPE_ARGUMENT(1) then TYPE_ARGUMENT(0) then WILDCARD_BOUND
		String path = "1;0;*"
		validate(path, sig) == null
	}

	@Nullable
	String validate(@Nullable String typePath, String signature) {
		TypePath typePathObj = typePath == null ? null : TypePath.fromString(typePath)
		TypePathCheckerVisitor visitor = new TypePathCheckerVisitor(typePathObj)
		new SignatureReader(signature).acceptType(visitor)
		return visitor.error
	}
}