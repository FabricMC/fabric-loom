/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.processors.aw;

import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.mappings.EntryTriple;

public class AccessWidenerRemapper {
	public static AccessWidener remap(AccessWidener input, Remapper remapper, String namespace) {
		if (input.namespace.equals(namespace)) {
			return input;
		}

		AccessWidener remapped = new AccessWidener(namespace);

		for (Map.Entry<String, AccessWidener.Access> entry : input.classAccess.entrySet()) {
			remapped.classAccess.put(remapper.map(entry.getKey()), entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessWidener.Access> entry : input.methodAccess.entrySet()) {
			remapped.addOrMerge(remapped.methodAccess, remapMethod(remapper, entry.getKey()), entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessWidener.Access> entry : input.fieldAccess.entrySet()) {
			remapped.addOrMerge(remapped.fieldAccess, remapField(remapper, entry.getKey()), entry.getValue());
		}

		return remapped;
	}

	private static EntryTriple remapMethod(Remapper remapper, EntryTriple entryTriple) {
		return new EntryTriple(
				remapper.map(entryTriple.getOwner()),
				remapper.mapMethodName(entryTriple.getOwner(), entryTriple.getName(), entryTriple.getDesc()),
				remapper.mapDesc(entryTriple.getDesc())
		);
	}

	private static EntryTriple remapField(Remapper remapper, EntryTriple entryTriple) {
		return new EntryTriple(
				remapper.map(entryTriple.getOwner()),
				remapper.mapFieldName(entryTriple.getOwner(), entryTriple.getName(), entryTriple.getDesc()),
				remapper.mapDesc(entryTriple.getDesc())
		);
	}
}
