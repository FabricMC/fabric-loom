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

package net.fabricmc.loom.api.mappings.intermediate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.util.download.DownloadBuilder;

/**
 * A simple API to allow 3rd party plugins.
 * Implement by creating an abstract class overriding provide and getName
 */
@ApiStatus.Experimental
public abstract class IntermediateMappingsProvider implements Named {
	public abstract Property<String> getMinecraftVersion();

	public abstract Property<Function<String, DownloadBuilder>> getDownloader();

	/**
	 * Generate or download a tinyv2 mapping file with intermediary and named namespaces.
	 * @throws IOException
	 */
	public abstract void provide(Path tinyMappings) throws IOException;

	public final void provideWithContext(Path tinyMappings, Project project) throws IOException {
		if (this instanceof Contextual ctx) {
			ctx.provide(tinyMappings, project);
		} else {
			this.provide(tinyMappings);
		}
	}

	public abstract static class Contextual extends IntermediateMappingsProvider {
		@Override
		public final void provide(Path tinyMappings) throws IOException {
			throw new UnsupportedOperationException();
		}

		public abstract void provide(Path tinyMappings, Project project) throws IOException;
	}
}
