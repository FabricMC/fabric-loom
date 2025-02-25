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

package net.fabricmc.loom.test.util

import groovy.transform.CompileStatic
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.MutationGuard
import org.gradle.api.internal.MutationGuards
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.DefaultFileLookup
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory
import org.gradle.internal.Factory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry

import static org.mockito.Mockito.mock

/**
 * Based on Gradle's own TestUtils, this class setups the Gradle DI system to be used within unit tests.
 */
@CompileStatic
class TestServiceFactory {
	public static final ServiceRegistry serviceRegistry = createServiceRegistry()
	public static final ObjectFactory objectFactory = serviceRegistry.get(ObjectFactory)
	public static final ProviderFactory providerFactory = serviceRegistry.get(ProviderFactory)

	private static ServiceRegistry createServiceRegistry() {
		def services = new DefaultServiceRegistry()
		services.register {
			it.add(DefaultPropertyFactory)
			it.add(ProviderFactory, new DefaultProviderFactory())
			it.add(PropertyHost, PropertyHost.NO_OP)
			it.add(NamedObjectInstantiator)
			it.add(CollectionCallbackActionDecorator, CollectionCallbackActionDecorator.NOOP)
			it.add(MutationGuard, MutationGuards.identity())
			it.add(FileCollectionFactory, fileCollectionFactory())
			it.add(DefaultDomainObjectCollectionFactory)
			it.add(CrossBuildInMemoryCacheFactory, new DefaultCrossBuildInMemoryCacheFactory(mock(ListenerManager)))
			//noinspection unused
			it.addProvider(new ServiceRegistrationProvider() {
						@Provides
						InstantiatorFactory createInstantiatorFactory(
								CrossBuildInMemoryCacheFactory crossBuildInMemoryCacheFactory) {
							return new DefaultInstantiatorFactory(
									crossBuildInMemoryCacheFactory,
									[],
									new OutputPropertyRoleAnnotationHandler([])
									)
						}

						@Provides
						FilePropertyFactory createFilePropertyFactory(
								PropertyHost propertyHost,
								FileCollectionFactory fileCollectionFactory) {
							return new DefaultFilePropertyFactory(
									propertyHost,
									fileResolver(),
									fileCollectionFactory)
						}

						@Provides
						ObjectFactory createObjectFactory(
								InstantiatorFactory instantiatorFactory,
								NamedObjectInstantiator namedObjectInstantiator,
								PropertyFactory propertyFactory,
								FilePropertyFactory filePropertyFactory,
								FileCollectionFactory fileCollectionFactory,
								DomainObjectCollectionFactory domainObjectCollectionFactory) {
							return new DefaultObjectFactory(
									instantiatorFactory.decorate(services),
									namedObjectInstantiator,
									mock(DirectoryFileTreeFactory),
									mock(Factory) as Factory<PatternSet>,
									propertyFactory,
									filePropertyFactory,
									DefaultTaskDependencyFactory.withNoAssociatedProject(),
									fileCollectionFactory,
									domainObjectCollectionFactory)
						}
					})
		}

		return services
	}

	private static FileResolver fileResolver() {
		return new DefaultFileLookup().getFileResolver(new File(".").absoluteFile)
	}

	private static FileCollectionFactory fileCollectionFactory() {
		return new DefaultFileCollectionFactory(
				fileResolver(),
				DefaultTaskDependencyFactory.withNoAssociatedProject(),
				mock(DirectoryFileTreeFactory),
				mock(Factory) as Factory<PatternSet>,
				PropertyHost.NO_OP,
				mock(FileSystem))
	}
}
