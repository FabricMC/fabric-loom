/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.api.mappings.layered;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.mappingio.MappingVisitor;

@ApiStatus.Experimental
public interface MappingLayer {
	void visit(MappingVisitor mappingVisitor) throws IOException;

	default MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.NAMED;
	}

	/**
	 * Provides a list of layer classes that this mapping layer depends on. If such a layer is not present an Exception will be thrown when trying to resolve the layer.
	 * @return A list of MappingLayer classes to depend on.
	 */
	default List<Class<? extends MappingLayer>> dependsOn() {
		return Collections.emptyList();
	}
}
