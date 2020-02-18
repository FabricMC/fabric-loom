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

package net.fabricmc.loom.processors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.fabricmc.mappings.EntryTriple;

public class AccessEscalator {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, Access> methodAccess = new HashMap<>();
	public Map<EntryTriple, Access> fieldAccess = new HashMap<>();

	public void read(BufferedReader reader) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (!header[0].equals("ae")) {
			throw new UnsupportedOperationException("Unsupported access escalator file format: " + header[0]);
		}

		if (namespace != null) {
			if (!namespace.equals(header[2])) {
				throw new RuntimeException("Namespace mismatch");
			}
		}

		namespace = header[2];

		String line;

		while ((line = reader.readLine()) != null) {
			if (line.isEmpty()) continue;

			//Comment handling
			int commentPos = line.indexOf('#');

			if (commentPos >= 0) {
				line = line.substring(0, commentPos);
			}

			String[] split = line.split("\t");
			Access access = parseAccess(split[0]);

			switch (split[1]) {
			case "class":
				if (classAccess.containsKey(split[2])) {
					classAccess.put(split[2], mergeAccess(access, classAccess.get(split[2])));
				} else {
					classAccess.put(split[2], access);
				}

				break;
			case "field":
				addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access);
				break;
			case "method":
				addOrMerge(methodAccess, new EntryTriple(split[2], split[3], split[4]), access);
				break;
			default:
				throw new UnsupportedOperationException("Unsupported type " + split[1]);
			}
		}
	}

	//Could possibly be cleaner but should do its job for now
	public void write(StringWriter writer) {
		writer.write("ea\\v1\t");
		writer.write(namespace);
		writer.write("\n");

		for (Map.Entry<String, Access> entry : classAccess.entrySet()) {
			writer.write(entry.getValue().name().toLowerCase(Locale.ROOT));
			writer.write("\tclass\t");
			writer.write(entry.getKey());
			writer.write("\n");
		}

		for (Map.Entry<EntryTriple, Access> entry : methodAccess.entrySet()) {
			writer.write(entry.getValue().name().toLowerCase(Locale.ROOT));
			writer.write("\tmethod\t");
			writer.write(entry.getKey().getOwner());
			writer.write("\t");
			writer.write(entry.getKey().getName());
			writer.write("\t");
			writer.write(entry.getKey().getDesc());
			writer.write("\n");
		}

		for (Map.Entry<EntryTriple, Access> entry : fieldAccess.entrySet()) {
			writer.write(entry.getValue().name().toLowerCase(Locale.ROOT));
			writer.write("\tfield\t");
			writer.write(entry.getKey().getOwner());
			writer.write("\t");
			writer.write(entry.getKey().getName());
			writer.write("\t");
			writer.write(entry.getKey().getDesc());
			writer.write("\n");
		}
	}

	void addOrMerge(Map<EntryTriple, Access> map, EntryTriple entry, Access access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		if (map.containsKey(entry)) {
			map.replace(entry, mergeAccess(map.get(entry), access));
		} else {
			map.put(entry, access);
		}
	}

	public void merge(AccessEscalator other) {
		if (namespace == null) {
			namespace = other.namespace;
		} else if (!namespace.equals(other.namespace)) {
			throw new RuntimeException("Namespace mismatch");
		}

		for (Map.Entry<String, Access> entry : other.classAccess.entrySet()) {
			if (classAccess.containsKey(entry.getKey())) {
				classAccess.replace(entry.getKey(), mergeAccess(classAccess.get(entry.getKey()), entry.getValue()));
			} else {
				classAccess.put(entry.getKey(), entry.getValue());
			}
		}

		for (Map.Entry<EntryTriple, Access> entry : other.methodAccess.entrySet()) {
			addOrMerge(methodAccess, entry.getKey(), entry.getValue());
		}

		for (Map.Entry<EntryTriple, Access> entry : other.fieldAccess.entrySet()) {
			addOrMerge(fieldAccess, entry.getKey(), entry.getValue());
		}
	}

	private Access parseAccess(String input) {
		switch (input.toLowerCase(Locale.ROOT)) {
		case "public":
			return Access.PUBLIC;
		case "protected":
			return Access.PROTECTED;
		default:
			throw new UnsupportedOperationException("Unknown access:" + input);
		}
	}

	private Access mergeAccess(Access a, Access b) {
		return Access.values()[Math.max(a.ordinal(), b.ordinal())];
	}

	public Access getClassAccess(String className) {
		return classAccess.getOrDefault(className, Access.DEFAULT);
	}

	public Access getFieldAccess(EntryTriple entryTriple) {
		return fieldAccess.getOrDefault(entryTriple, Access.DEFAULT);
	}

	public Access getMethodAccess(EntryTriple entryTriple) {
		return methodAccess.getOrDefault(entryTriple, Access.DEFAULT);
	}

	public Set<String> getTargets() {
		Set<String> classes = new LinkedHashSet<>(classAccess.keySet());
		methodAccess.keySet().stream()
				.map(EntryTriple::getName).forEach(classes::add);
		fieldAccess.keySet().stream()
				.map(EntryTriple::getName).forEach(classes::add);
		return classes;
	}

	public enum Access {
		DEFAULT,
		PROTECTED,
		PUBLIC;

		public int apply(int access) {
			if (this == DEFAULT) { //Do nothing
				return access;
			} else if (this == PROTECTED) {
				if ((access & (Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) != 0) { //Either public or protected already, do nothing
					return access;
				}

				return (access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED; //Remove private and add protected
			} else {
				if ((access & Opcodes.ACC_PUBLIC) != 0) { //Already public do nothing.
					return access;
				}

				return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC; //Remove private or protected and add public
			}
		}
	}
}
