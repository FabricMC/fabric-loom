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
import org.gradle.api.Project
import org.mockito.Mockito
import spock.lang.Specification

import net.fabricmc.loom.test.util.GradleTestUtil
import net.fabricmc.loom.util.service.ScopedServiceFactory
import net.fabricmc.loom.util.service.Service
import net.fabricmc.loom.util.service.ServiceFactory
import net.fabricmc.loom.util.service.ServiceType

@CompileStatic
abstract class ServiceTestBase extends Specification {
	ServiceFactory factory
	Project project = GradleTestUtil.mockProject()

	private Map<Service.Options, Service> mockedServices = new IdentityHashMap<>()
	private ScopedServiceFactory scopedServiceFactory = new ScopedServiceFactory()

	def setup() {
		this.scopedServiceFactory = new ScopedServiceFactory() {
					@Override
					protected ServiceFactory getEffectiveServiceFactory() {
						return factory
					}
				}
		this.factory = new ServiceFactory() {
					@Override
					<O extends Service.Options, S extends Service<O>> S get(O options) {
						def self = ServiceTestBase.this
						return mockedServices.get(options) as S ?: scopedServiceFactory.get(options) as S
					}
				}
	}

	Service.Options mockService(ServiceType type) {
		Service.Options options = project.getObjects().newInstance(type.optionsClass())

		for (def method : type.optionsClass().getDeclaredMethods()) {
			method.invoke(options)
		}

		options.serviceClass.set(type.serviceClass().name)

		Service mocked = Mockito.mock(type.serviceClass())
		mockedServices.put(options, mocked)
		return options
	}

	def cleanup() {
		scopedServiceFactory.close()
		scopedServiceFactory = null
	}
}
