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

package net.fabricmc.loom.util;

import net.fabricmc.mappings.*;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.MemberInstance;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() {

	}

	public static IMappingProvider create(Mappings mappings, String from, String to) {
		return (classMap, fieldMap, methodMap) -> {
			for (ClassEntry entry : mappings.getClassEntries()) {
				classMap.put(entry.get(from), entry.get(to));
			}

			for (FieldEntry entry : mappings.getFieldEntries()) {
				EntryTriple fromTriple = entry.get(from);
				fieldMap.put(fromTriple.getOwner() + "/" + MemberInstance.getFieldId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
			}

			for (MethodEntry entry : mappings.getMethodEntries()) {
				EntryTriple fromTriple = entry.get(from);
				methodMap.put(fromTriple.getOwner() + "/" + MemberInstance.getMethodId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
			}
		};
	}
}
