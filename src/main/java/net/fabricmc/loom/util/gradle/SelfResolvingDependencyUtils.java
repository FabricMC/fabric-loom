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

package net.fabricmc.loom.util.gradle;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import javax.annotation.Nullable;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;

// SelfResolvingDependency is deprecated for removal, use reflection to ensure backwards compat.
@Deprecated
public class SelfResolvingDependencyUtils {
	// Set this system prop to disable SRD support before Gradle does.
	public static final boolean DISABLE_SRD_SUPPORT = System.getProperty("fabric.loom.disable.srd") != null;

	private static final String SELF_RESOLVING_DEPENDENCY_CLASS_NAME = "org.gradle.api.artifacts.SelfResolvingDependency";
	@Nullable
	private static final Class<?> SELF_RESOLVING_DEPENDENCY_CLASS = getSelfResolvingDependencyOrNull();
	@Nullable
	private static final Method RESOLVE_METHOD = getResolveMethod(SELF_RESOLVING_DEPENDENCY_CLASS);

	/**
	 * @return true when dependency is a SelfResolvingDependency but NOT a FileCollectionDependency.
	 */
	public static boolean isExplicitSRD(Dependency dependency) {
		// FileCollectionDependency is usually the replacement for SelfResolvingDependency
		if (dependency instanceof FileCollectionDependency) {
			return false;
		} else if (dependency instanceof ProjectDependency) {
			return false;
		}

		return isSRD(dependency);
	}

	private static boolean isSRD(Dependency dependency) {
		if (SELF_RESOLVING_DEPENDENCY_CLASS == null) {
			return false;
		}

		return dependency.getClass().isAssignableFrom(SELF_RESOLVING_DEPENDENCY_CLASS);
	}

	public static Set<File> resolve(Dependency dependency) {
		if (!isSRD(dependency)) {
			throw new IllegalStateException("dependency is not a SelfResolvingDependency");
		}

		try {
			return (Set<File>) RESOLVE_METHOD.invoke(dependency);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException("Failed to resolve SelfResolvingDependency", e);
		}
	}

	@Nullable
	private static Class<?> getSelfResolvingDependencyOrNull() {
		if (DISABLE_SRD_SUPPORT) {
			// Lets pretend SRD doesnt exist.
			return null;
		}

		try {
			return Class.forName(SELF_RESOLVING_DEPENDENCY_CLASS_NAME);
		} catch (ClassNotFoundException e) {
			// Gradle 9+
			return null;
		}
	}

	@Nullable
	private static Method getResolveMethod(Class<?> clazz) {
		if (clazz == null) {
			// Gradle 9+
			return null;
		}

		try {
			var method = clazz.getDeclaredMethod("resolve");
			method.setAccessible(true);
			return method;
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Failed to get SelfResolvingDependency.resolve() method", e);
		}
	}
}
