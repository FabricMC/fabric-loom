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

package net.fabricmc.loom.api;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

public interface InterfaceInjectionExtensionAPI {
	/**
	 * When true loom will inject interfaces declared in dependency mod manifests into the minecraft jar file.
	 * This is used to expose interfaces that are implemented on Minecraft classes by mixins at runtime
	 * in the dev environment.
	 *
	 * @return the property controlling interface injection.
	 */
	Property<Boolean> getEnableDependencyInterfaceInjection();

	/**
	 * Contains a list of {@link SourceSet} that may contain a mod metadata file with interfaces to inject.
	 * By default, this list contains only the main {@link SourceSet}.
	 *
	 * @return the list property containing the {@link SourceSet}
	 */
	ListProperty<SourceSet> getInterfaceInjectionSourceSets();

	default boolean isEnabled() {
		return getEnableDependencyInterfaceInjection().get() || !getInterfaceInjectionSourceSets().get().isEmpty();
	}
}
