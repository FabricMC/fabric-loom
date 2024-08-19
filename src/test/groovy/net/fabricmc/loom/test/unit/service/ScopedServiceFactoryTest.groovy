/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.test.unit.service

import groovy.transform.InheritConstructors
import groovy.transform.TupleConstructor
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.service.ScopedServiceFactory
import net.fabricmc.loom.util.service.Service
import net.fabricmc.loom.util.service.ServiceType

class ScopedServiceFactoryTest extends Specification {
	def "create service"() {
		given:
		def options = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def factory = new ScopedServiceFactory()

		when:
		TestService service = factory.get(options)

		then:
		service.getExample() == "hello"

		cleanup:
		factory.close()
	}

	def "reuse service"() {
		given:
		def options = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def factory = new ScopedServiceFactory()

		when:
		TestService service = factory.get(options)
		TestService service2 = factory.get(options)

		then:
		service === service2

		cleanup:
		factory.close()
	}

	def "reuse service different options instance"() {
		given:
		def options = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def options2 = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def factory = new ScopedServiceFactory()

		when:
		TestService service = factory.get(options)
		TestService service2 = factory.get(options2)

		then:
		service === service2

		cleanup:
		factory.close()
	}

	def "Separate instances"() {
		given:
		def options = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def options2 = new TestServiceOptions(GradleTestUtil.mockProperty("world"))
		def factory = new ScopedServiceFactory()

		when:
		TestService service = factory.get(options)
		TestService service2 = factory.get(options2)

		then:
		service !== service2
		service.example == "hello"
		service2.example == "world"

		cleanup:
		factory.close()
	}

	def "close service"() {
		given:
		def options = new TestServiceOptions(GradleTestUtil.mockProperty("hello"))
		def factory = new ScopedServiceFactory()

		when:
		TestService service = factory.get(options)
		factory.close()

		then:
		service.closed
	}

	@InheritConstructors
	static class TestService extends Service<Options> implements Closeable {
		static ServiceType<TestService.Options, TestService> TYPE = new ServiceType(TestService.Options.class, TestService.class)

		interface Options extends Service.Options {
			@Input
			Property<String> getExample();
		}

		boolean closed = false

		String getExample() {
			return options.example.get()
		}

		@Override
		void close() throws Exception {
			closed = true
		}
	}

	@TupleConstructor
	static class TestServiceOptions implements TestService.Options {
		Property<String> example
		Property<String> serviceClass = ServiceTestBase.serviceClassProperty(TestService.TYPE)
	}
}
