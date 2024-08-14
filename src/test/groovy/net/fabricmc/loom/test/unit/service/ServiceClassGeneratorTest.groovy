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

import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.newService.Service
import net.fabricmc.loom.util.newService.ServiceClassGenerator
import net.fabricmc.loom.util.newService.ServiceFactory

class ServiceClassGeneratorTest extends Specification {
	def "generate and load class"() {
		given:
		def generator = new ServiceClassGenerator<>(TestService.class)
		def serviceFactory = Mock(ServiceFactory.class)
		def options = new TestService.Options() {
					Property<String> example = GradleTestUtil.mockProperty("Hello World")
					Property<String> serviceClass = GradleTestUtil.mockProperty(TestService.class.name)

					@Override
					Property<String> getServiceClass() {
						return serviceClass
					}
				}

		when:
		TestService instance = generator.generateAndInstantiate(serviceFactory, options)

		then:
		instance instanceof TestService
		instance.getTestServiceFactory() == serviceFactory
		instance.getExample() == "Hello World"
	}
}

@CompileStatic
abstract class TestService extends Service<Options> {
	interface Options extends Service.Options {
		@Input
		Property<String> getExample();
	}

	String getExample() {
		return options().example.get()
	}

	ServiceFactory getTestServiceFactory() {
		return serviceFactory()
	}
}