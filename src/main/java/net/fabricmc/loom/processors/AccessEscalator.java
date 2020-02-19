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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.fabricmc.mappings.EntryTriple;

public class AccessEscalator {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> methodAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> fieldAccess = new HashMap<>();

	public void read(BufferedReader reader) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (header.length != 2 || !header[0].equals("ae\\v1")) {
			throw new UnsupportedOperationException("Unsupported or invalid access escalator file");
		}

		if (namespace != null) {
			if (!namespace.equals(header[1])) {
				throw new RuntimeException("Namespace mismatch");
			}
		}

		namespace = header[1];

		String line;

		while ((line = reader.readLine()) != null) {
			//Comment handling
			int commentPos = line.indexOf('#');

			if (commentPos >= 0) {
				line = line.substring(0, commentPos).trim();
			}

			if (line.isEmpty()) continue;

			//Will be a common issue, make it clear.
			if (line.contains(" ")) {
				throw new RuntimeException("Access escalator contains spaces, tabs are required on line: " + line);
			}

			String[] split = line.split("\t");

			if (split.length != 3 && split.length != 5) {
				throw new RuntimeException("Failed to parse access escalator. at line:" + line);
			}

			Access access = parseAccess(split[0]);

			switch (split[1]) {
			case "class":
				if (split.length != 3) {
					throw new RuntimeException("Failed to parse access escalator. at line:" + line);
				}

				if (classAccess.containsKey(split[2])) {
					classAccess.put(split[2], mergeAccess(access, classAccess.get(split[2])));
				} else {
					classAccess.put(split[2], access);
				}

				break;
			case "field":
				if (split.length != 5) {
					throw new RuntimeException("Failed to parse access escalator. at line:" + line);
				}

				addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access);
				break;
			case "method":
				if (split.length != 5) {
					throw new RuntimeException("Failed to parse access escalator. at line:" + line);
				}

				addOrMerge(methodAccess, new EntryTriple(split[2], split[3], split[4]), access);
				break;
			default:
				throw new UnsupportedOperationException("Unsupported type " + split[1]);
			}
		}
	}

	//Could possibly be cleaner but should do its job for now
	public void write(StringWriter writer) {
		writer.write("ae\\v1\t");
		writer.write(namespace);
		writer.write("\n");

		for (Map.Entry<String, Access> entry : classAccess.entrySet()) {
			writer.write(entry.getValue().name().toLowerCase(Locale.ROOT));
			writer.write("\tclass\t");
			writer.write(entry.getKey());
			writer.write("\n");
		}

		for (Map.Entry<EntryTriple, ChangeList> entry : methodAccess.entrySet()) {
			for (Access access : entry.getValue().getChanges()) {
				writer.write(access.name().toLowerCase(Locale.ROOT));
				writer.write("\tmethod\t");
				writer.write(entry.getKey().getOwner());
				writer.write("\t");
				writer.write(entry.getKey().getName());
				writer.write("\t");
				writer.write(entry.getKey().getDesc());
				writer.write("\n");
			}
		}

		for (Map.Entry<EntryTriple, ChangeList> entry : fieldAccess.entrySet()) {
			for (Access access : entry.getValue().getChanges()) {
				writer.write(access.name().toLowerCase(Locale.ROOT));
				writer.write("\tfield\t");
				writer.write(entry.getKey().getOwner());
				writer.write("\t");
				writer.write(entry.getKey().getName());
				writer.write("\t");
				writer.write(entry.getKey().getDesc());
				writer.write("\n");
			}
		}
	}

	void addOrMerge(Map<EntryTriple, ChangeList> map, EntryTriple entry, ChangeList changeList) {
		for (Access access : changeList.getChanges()) {
			addOrMerge(map, entry, access);
		}
	}

	void addOrMerge(Map<EntryTriple, ChangeList> map, EntryTriple entry, Access access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		if (map.containsKey(entry)) {
			map.get(entry).add(access);
		} else {
			map.put(entry, new ChangeList(access));
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

		for (Map.Entry<EntryTriple, ChangeList> entry : other.methodAccess.entrySet()) {
			addOrMerge(methodAccess, entry.getKey(), entry.getValue());
		}

		for (Map.Entry<EntryTriple, ChangeList> entry : other.fieldAccess.entrySet()) {
			addOrMerge(fieldAccess, entry.getKey(), entry.getValue());
		}
	}

	private Access parseAccess(String input) {
		switch (input.toLowerCase(Locale.ROOT)) {
		case "public":
			return Access.PUBLIC;
		case "protected":
			return Access.PROTECTED;
		case "mutable":
			return Access.MUTABLE;
		default:
			throw new UnsupportedOperationException("Unknown access:" + input);
		}
	}

	private static Access mergeAccess(Access a, Access b) {
		return Access.values()[Math.max(a.ordinal(), b.ordinal())];
	}

	public Access getClassAccess(String className) {
		return classAccess.getOrDefault(className, Access.DEFAULT);
	}

	public ChangeList getFieldAccess(EntryTriple entryTriple) {
		return fieldAccess.getOrDefault(entryTriple, ChangeList.EMPTY);
	}

	public ChangeList getMethodAccess(EntryTriple entryTriple) {
		return methodAccess.getOrDefault(entryTriple, ChangeList.EMPTY);
	}

	public Set<String> getTargets() {
		Set<String> classes = new LinkedHashSet<>(classAccess.keySet());
		methodAccess.keySet().stream()
				.map(EntryTriple::getName).forEach(classes::add);
		fieldAccess.keySet().stream()
				.map(EntryTriple::getName).forEach(classes::add);

		Set<String> parentClasses = new LinkedHashSet<>();

		//Also transform all parent classes
		for (String clazz : classes) {
			while (clazz.contains("$")) {
				clazz = clazz.substring(0, clazz.lastIndexOf("$"));
				parentClasses.add(clazz);
			}
		}

		classes.addAll(parentClasses);

		return classes;
	}

	public enum Access {
		DEFAULT,
		PROTECTED,
		PUBLIC,

		MUTABLE(true);

		private final boolean exclusive;

		Access(boolean exclusive) {
			this.exclusive = exclusive;
		}

		Access() {
			this(false);
		}

		public int apply(int access) {
			switch (this) {
			case DEFAULT:
				return access;
			case PUBLIC:
				return (access & ~7) | Opcodes.ACC_PUBLIC;
			case PROTECTED:
				if ((access & Opcodes.ACC_PUBLIC) != 0) { //Already public
					return access;
				}

				return (access & ~7) | Opcodes.ACC_PROTECTED;
			case MUTABLE:
				return access & ~Opcodes.ACC_FINAL; //Remove final
			default:
				throw new RuntimeException("Something bad happened");
			}
		}
	}

	public static class ChangeList {
		public static final ChangeList EMPTY = new ChangeList();

		private final List<Access> changes = new ArrayList<>();

		public ChangeList() {
		}

		public ChangeList(Access access) {
			this();
			add(access);
		}

		public void add(Access access) {
			if (access.exclusive && !changes.contains(access)) {
				changes.add(access);
			} else {
				if (changes.isEmpty() || changes.stream().allMatch(a -> a.exclusive)) {
					changes.add(access);
				} else {
					Access existing = null;

					for (Access change : changes) {
						if (!change.exclusive) {
							if (existing != null) {
								throw new RuntimeException("More than one change");
							}

							existing = change;
						}
					}

					if (existing == null) {
						throw new RuntimeException("Failed to find existing, something has gone wrong");
					}

					changes.remove(existing);
					changes.add(mergeAccess(existing, access));
				}
			}
		}

		public void merge(ChangeList changeList) {
			for (Access change : changeList.getChanges()) {
				add(change);
			}
		}

		public int apply(int access) {
			for (Access change : getChanges()) {
				access = change.apply(access);
			}

			return access;
		}

		public List<Access> getChanges() {
			return changes;
		}
	}
}
