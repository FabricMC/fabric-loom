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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A class generator for creating implementations of a service class.
 * This is needed to provide implementations of the serviceFactory and options fields.
 * The generated class will have a constructor that takes a ServiceFactory and Service.Options parameter.
 * @param <C> The service class type.
 */
public final class ServiceClassGenerator<C> {
	private static final String SERVICE_FACTORY_NAME = "serviceFactory";
	private static final String OPTIONS_NAME = "options";

	private final Class<C> superClass;

	public ServiceClassGenerator(Class<C> superClass) {
		this.superClass = superClass;
	}

	/**
	 * Generates and loads new class that extends the target super class and implements the serviceFactory and options fields.
	 * @param serviceFactory The {@link ServiceFactory} to use.
	 * @param options The {@link Service.Options} used to create the service.
	 * @return an {@link C} instance of the generated class.
	 */
	public C generateAndInstantiate(ServiceFactory serviceFactory, Service.Options options) {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		byte[] bytecode = generate();

		try {
			var classLookup = lookup.defineHiddenClass(bytecode, true);
			Class<?> clazz = classLookup.lookupClass();
			Constructor<?> constructor = clazz.getDeclaredConstructor(ServiceFactory.class, Service.Options.class);
			//noinspection unchecked
			return (C) constructor.newInstance(serviceFactory, options);
		} catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
			throw new RuntimeException("Failed to instantiate generated class", e);
		}
	}

	// Must be in the same package as the lookup class
	private String getClassName() {
		return this.getClass().getPackage().getName() + "." + superClass.getSimpleName() + "_LoomGenerated";
	}

	private void validateSuperClass() {
		if (!Modifier.isAbstract(superClass.getModifiers())) {
			throw new IllegalArgumentException("Super class must be abstract");
		}

		Constructor<?>[] constructors = superClass.getDeclaredConstructors();

		if (constructors.length != 1 || constructors[0].getParameterCount() != 0) {
			throw new IllegalArgumentException("Super class must have a single no args constructor");
		}
	}

	private byte[] generate() {
		validateSuperClass();
		final Type thisType = Type.getObjectType(getClassName().replace('.', '/'));

		// Create a class that implements the target super class
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(52, Modifier.FINAL | Modifier.PUBLIC, getClassName().replace('.', '/'), null, getClassName(superClass), null);

		// Add a field for the serviceFactory
		cw.visitField(Modifier.PRIVATE | Modifier.FINAL, SERVICE_FACTORY_NAME, getFieldDesc(ServiceFactory.class), null, null).visitEnd();
		// Add a field for the Service.Options
		cw.visitField(Modifier.PRIVATE | Modifier.FINAL, OPTIONS_NAME, getFieldDesc(Service.Options.class), null, null).visitEnd();

		// Add a constructor that takes a ServiceFactory parameter and options and sets the serviceFactory and options fields
		final GeneratorAdapter constructor = createMethod(cw, Modifier.PUBLIC, "<init>", getMethodDesc(Void.TYPE, ServiceFactory.class, Service.Options.class));
		constructor.loadThis();
		constructor.invokeConstructor(Type.getType(superClass), Method.getMethod(superClass.getDeclaredConstructors()[0]));
		constructor.loadThis();
		constructor.loadArg(0);
		constructor.putField(thisType, SERVICE_FACTORY_NAME, Type.getType(ServiceFactory.class));
		constructor.loadThis();
		constructor.loadArg(1);
		constructor.putField(thisType, OPTIONS_NAME, Type.getType(Service.Options.class));
		constructor.returnValue();
		constructor.endMethod();

		// Add a "ServiceFactory serviceFactory()" method that returns the serviceFactory field
		final GeneratorAdapter serviceFactoryMethod = createMethod(cw, Modifier.PROTECTED, SERVICE_FACTORY_NAME, getMethodDesc(ServiceFactory.class));
		serviceFactoryMethod.loadThis();
		serviceFactoryMethod.getField(thisType, SERVICE_FACTORY_NAME, Type.getType(ServiceFactory.class));
		serviceFactoryMethod.returnValue();
		serviceFactoryMethod.endMethod();

		// Add a "Service.Options options()" method that returns the options field
		final GeneratorAdapter optionsMethod = createMethod(cw, Modifier.PROTECTED, OPTIONS_NAME, getMethodDesc(Service.Options.class));
		optionsMethod.loadThis();
		optionsMethod.getField(thisType, OPTIONS_NAME, Type.getType(Service.Options.class));
		optionsMethod.returnValue();
		optionsMethod.endMethod();

		cw.visitEnd();
		return cw.toByteArray();
	}

	private GeneratorAdapter createMethod(ClassWriter cw, int access, String name, String desc) {
		return new GeneratorAdapter(cw.visitMethod(access, name, desc, null, null), access, name, desc);
	}

	private static String getClassName(Class<?> clazz) {
		return clazz.getName().replace('.', '/');
	}

	private static String getMethodArgsDesc(Class<?>... args) {
		StringBuilder desc = new StringBuilder("(");

		for (Class<?> arg : args) {
			desc.append(Type.getDescriptor(arg));
		}

		desc.append(")");
		return desc.toString();
	}

	private static String getMethodDesc(Class<?> returnType, Class<?>... args) {
		return getMethodArgsDesc(args) + Type.getDescriptor(returnType);
	}

	private static String getFieldDesc(Class<?> type) {
		return Type.getDescriptor(type);
	}
}
