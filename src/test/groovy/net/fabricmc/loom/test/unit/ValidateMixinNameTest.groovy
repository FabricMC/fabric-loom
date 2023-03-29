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

package net.fabricmc.loom.test.unit

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import spock.lang.Specification

import net.fabricmc.loom.task.ValidateMixinNameTask

class ValidateMixinNameTest extends Specification {
	def "TestMixin"() {
		when:
		def mixin = getMixin(TestMixin.class)
		then:
		mixin.className() == "net/fabricmc/loom/test/unit/TestMixin"
		mixin.target().internalName == "net/fabricmc/loom/test/unit/Test"
		mixin.expectedClassName() == "TestMixin"
		!mixin.accessor()
	}

	def "TestInnerMixin"() {
		when:
		def mixin = getMixin(TestInnerMixin.class)
		then:
		mixin.className() == "net/fabricmc/loom/test/unit/TestInnerMixin"
		mixin.target().internalName == "net/fabricmc/loom/test/unit/Test\$Inner"
		mixin.expectedClassName() == "TestInnerMixin"
		!mixin.accessor()
	}

	def "TestAccessor"() {
		when:
		def mixin = getMixin(TestAccessor.class)
		then:
		mixin.className() == "net/fabricmc/loom/test/unit/TestAccessor"
		mixin.target().internalName == "net/fabricmc/loom/test/unit/Test"
		mixin.expectedClassName() == "TestAccessor"
		mixin.accessor()
	}

	def "TestManyTargetsMixin"() {
		when:
		def mixin = getMixin(TestManyTargetsMixin.class)
		then:
		mixin == null
	}

	static ValidateMixinNameTask.Mixin getMixin(Class<?> clazz) {
		return getInput(clazz).withCloseable {
			return ValidateMixinNameTask.getMixin(it)
		}
	}

	static InputStream getInput(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class")
	}
}

@Mixin(Test.class)
class TestMixin {
}

@Mixin(Test.Inner.class)
class TestInnerMixin {
}

@Mixin(Test.class)
interface TestAccessor {
	@Accessor
	Object getNothing();
}

@Mixin([Test.class, Test.Inner.class])
class TestManyTargetsMixin {
}

class Test {
	class Inner {
	}
}