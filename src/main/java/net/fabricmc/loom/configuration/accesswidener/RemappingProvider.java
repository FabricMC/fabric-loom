/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration.accesswidener;

import net.fabricmc.accesswidener.RemappingDecorator;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.TinyRemapper;

import org.gradle.api.Project;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RemappingProvider implements RemappingDecorator.Provider, AutoCloseable {
	// Key is the source namespace, values are lazily populated
	private final Map<String, TinyRemapper> tinyRemappers = new HashMap<>();
	private final Project project;
	private List<String> validNamespaces;

	public RemappingProvider(Project project) {
		this.project = project;
	}

	@Override
	public Remapper getRemapper(String from, String to) {
		if (from.equals(to)) {
			return null; // Nice! Nothing to do!
		}

		try {
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			if (validNamespaces == null) {
				validNamespaces = extension.getMappingsProvider().getMappings().getMetadata().getNamespaces();
			}

			if (!validNamespaces.contains(from)) {
				throw new UnsupportedOperationException(String.format("Access Widener namespace '%s' is not a valid namespace, it must be one of: '%s'", from, String.join(", ", validNamespaces)));
			}

			TinyRemapper tinyRemapper = tinyRemappers.get(from);
			if (tinyRemapper == null) {
				tinyRemapper = TinyRemapperHelper.getTinyRemapper(project, from, to);
				tinyRemapper.readClassPath(TinyRemapperHelper.getRemapClasspath(project));
				tinyRemappers.put(from, tinyRemapper);
			}
			return tinyRemapper.getRemapper();
		} catch (IOException e) {
			throw new RuntimeException("Failed to load mapping data for remapping access wideners", e);
		}
	}

	@Override
	public void close() {
		tinyRemappers.values().forEach(TinyRemapper::finish);
		tinyRemappers.clear();
	}
}
