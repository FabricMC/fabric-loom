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

package net.fabricmc.loom.configuration;

import java.io.File;
import java.io.Reader;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.ZipUtils;

@ApiStatus.Experimental // This may change at any time as new features are added to Loom
public interface ModMetadataHelper extends Serializable {
	String getFileName();

	/**
	 * @throws UnsupportedOperationException if the mod metadata cannot be read
	 */
	Metadata createMetadata(File input);
	Metadata createMetadata(Path input);
	Metadata createMetadata(Reader reader);

	ZipUtils.UnsafeUnaryOperator<JsonObject> stripNestedJarsFunction();

	/**
	 * @throws IllegalStateException in the returned runnable if any duplicated nested jars are added
	 */
	ZipUtils.UnsafeUnaryOperator<JsonObject> addNestedJarsFunction(List<String> files);

	interface Metadata {
		ModMetadataHelper getParent();
		Collection<String> getMixinConfigurationFiles();

		/**
		 * @return null if the provided mod metadata does not include a version
		 */
		@Nullable
		String getVersion();

		/**
		 * The name of this mod, e.g. "Fabric Example Mod"
		 * @return null if the provided mod metadata does not include a name
		 */
		@Nullable
		String getName();

		/**
		 * The id of this mod, e.g. "fabric-example-mod"
		 * @return null if the provided mod metadata does not include an id
		 */
		@Nullable
		String getId();

		/**
		 * The path to the access widener of this mod.
		 * @return null if the provided mod metadata does not include an access widener file
		 */
		@Nullable
		String getAccessWidener();

		/**
		 * @return the list of injected interfaces. May be empty, but never null.
		 */
		List<InjectedInterface> getInjectedInterfaces();

		record InjectedInterface(String modId, String className, String ifaceName) {
		}
	}
}
