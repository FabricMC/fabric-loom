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

package net.fabricmc.loom.util.newService;

import java.io.Closeable;
import java.io.IOException;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

// Note: This file mostly acts as documentation for the new service system.
// Services are classes that wrap expensive to create objects, such as mappings or tiny remapper.
// Services can be reused multiple times within a given scope, such as configuration or a single task action.
// Services need to have serializable inputs, used as a cache key and serialised to be passed between Gradle contexts. E.g task inputs, or work action params.
public final class ExampleService extends Service<ExampleService.Options> implements Closeable {
	// Options use Gradle's Property's thus can be used in task inputs.
	public interface Options extends Service.Options {
		@Nested
		Property<AnotherService.Options> nested();
	}

	// Options can be created using data from the Project
	static Provider<Options> createOptions(Project project) {
		return Service.createOptions(project, ExampleService.class, ExampleService.Options.class, o -> {
			o.nested().set(AnotherService.createOptions(project, "example"));
		});
	}

	// An example of how a service could be used, this could be within a task action.
	// ServiceFactory would be similar to the existing ScopedSharedServiceManager
	// Thus if a service with the same options has previously been created it will be reused.
	static void howToUse(Options options, ServiceFactory factory) {
		ExampleService exampleService = factory.get(options);
		exampleService.doSomething();
	}

	public ExampleService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public void doSomething() {
		// The service factory used to the creation the current service can be used to get or create other services based on the current service's options.
		AnotherService another = getServiceFactory().get(getOptions().nested());
		System.out.println("ExampleService: " + another.getExample());
	}

	@Override
	public void close() throws IOException {
		// Anything that needs to be cleaned up when the service is no longer needed.
	}

	public static final class AnotherService extends Service<AnotherService.Options> {
		public interface Options extends Service.Options {
			@Input
			Property<String> example();
		}

		static Provider<AnotherService.Options> createOptions(Project project, String example) {
			return Service.createOptions(project, AnotherService.class, AnotherService.Options.class, o -> {
				o.example().set(example);
			});
		}

		public AnotherService(Options options, ServiceFactory serviceFactory) {
			super(options, serviceFactory);
		}

		// Services can expose any methods they wish, either to return data or do a job.
		public String getExample() {
			return getOptions().example().get();
		}
	}
}
