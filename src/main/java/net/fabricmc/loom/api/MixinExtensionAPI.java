/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface MixinExtensionAPI {
	Property<Boolean> getUseLegacyMixinAp();

	Property<String> getDefaultRefmapName();

	Property<String> getRefmapTargetNamespace();

	/**
	 * Apply Mixin AP to sourceSet.
	 * @param sourceSet the sourceSet that applies Mixin AP.
	 * @param refmapName the output ref-map name. By default this will be {@link #getDefaultRefmapName()}
	 * @param action used for filter the mixin json files. By default this will be all files
	 *                  with name {@code *.mixins.json} that is inside the {@code resources} folder
	 *                  of {@code sourceSet}.
	 */
	void add(SourceSet sourceSet, String refmapName, Action<PatternSet> action);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSet the sourceSet that applies Mixin AP.
	 * @param refmapName the output ref-map name.
	 */
	void add(SourceSet sourceSet, String refmapName);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSet the sourceSet that applies Mixin AP.
	 * @param action used for filter the mixin json files.
	 */
	void add(SourceSet sourceSet, Action<PatternSet> action);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSet the sourceSet that applies Mixin AP.
	 */
	void add(SourceSet sourceSet);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSetName the name of sourceSet that applies Mixin AP.
	 * @param refmapName the output ref-map name.
	 * @param action used for filter the mixin json files.
	 */
	void add(String sourceSetName, String refmapName, Action<PatternSet> action);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSetName the name of sourceSet that applies Mixin AP.
	 * @param refmapName the output ref-map name.
	 */
	void add(String sourceSetName, String refmapName);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSetName the name of sourceSet that applies Mixin AP.
	 * @param action used for filter the mixin json files.
	 */
	void add(String sourceSetName, Action<PatternSet> action);

	/**
	 * Apply Mixin AP to sourceSet. See {@link MixinExtensionAPI#add(SourceSet, String, Action)} for more detail.
	 * @param sourceSetName the name of sourceSet that applies Mixin AP.
	 */
	void add(String sourceSetName);
}
