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

public class AccessWidener {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, Access> methodAccess = new HashMap<>();
	public Map<EntryTriple, Access> fieldAccess = new HashMap<>();
	private Set<String> classes = new LinkedHashSet<>();

	public void read(BufferedReader reader) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (header.length != 2 || !header[0].equals("accessWidener\\v1")) {
			throw new UnsupportedOperationException("Unsupported or invalid access accessWidener file, expected: accessWidener\\v1 <namespace>");
		}

		if (namespace != null) {
			if (!namespace.equals(header[1])) {
				throw new RuntimeException(String.format("Namespace mismatch, expected %s got %s", namespace, header[1]));
			}
		}

		namespace = header[1];
		Set<String> targets = new LinkedHashSet<>();
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
				throw new RuntimeException("AccessWidener contains one or more space character, tabs are required on line: " + line);
			}

			String[] split = line.split("\t");

			if (split.length != 3 && split.length != 5) {
				throw new RuntimeException(String.format("Invalid line (%s)", line));
			}

			String access = split[0];

			targets.add(split[2].replaceAll("/", "."));

			switch (split[1]) {
				case "class":
					if (split.length != 3) {
						throw new RuntimeException(String.format("Expected (<access>\tclass\t<className>) got (%s)", line));
					}

					classAccess.put(split[2], applyAccess(access, classAccess.getOrDefault(split[2], Access.DEFAULT)));
					break;
				case "field":
					if (split.length != 5) {
						throw new RuntimeException(String.format("Expected (<access>\tfield\t<className>\t<fieldName>\t<fieldDesc>) got (%s)", line));
					}

					addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access);
					break;
				case "method":
					if (split.length != 5) {
						throw new RuntimeException(String.format("Expected (<access>\tmethod\t<className>\t<methodName>\t<methodDesc>) got (%s)", line));
					}

					addOrMerge(methodAccess, new EntryTriple(split[2], split[3], split[4]), access);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported type " + split[1]);
			}
		}

		Set<String> parentClasses = new LinkedHashSet<>();

		//Also transform all parent classes
		for (String clazz : targets) {
			while (clazz.contains("$")) {
				clazz = clazz.substring(0, clazz.lastIndexOf("$"));
				parentClasses.add(clazz);
			}
		}

		classes.addAll(targets);
		classes.addAll(parentClasses);
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

		Access merged = Access.DEFAULT;
		if (access.makeProtected) {
			merged = merged.makeProtected();
		}
		if (access.makePublic) {
			merged = merged.makePublic();
		}
		if (access.stripFinal) {
			merged = merged.stripFinal();
		}

		map.put(entry, merged);
	}

	void addOrMerge(Map<EntryTriple, Access> map, EntryTriple entry, String access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		map.put(entry, applyAccess(access, map.getOrDefault(entry, Access.DEFAULT)));
	}

	public void merge(AccessWidener other) {
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

	private Access applyAccess(String input, Access access) {
		switch (input.toLowerCase(Locale.ROOT)) {
			case "public":
				return access.makePublic();
			case "protected":
				return access.makeProtected();
			case "stripfinal":
				return access.stripFinal();
			default:
				throw new UnsupportedOperationException("Unknown access type:" + input);
		}
	}

	private static Access mergeAccess(Access a, Access b) {
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
		return classes;
	}

	public enum Access {
		DEFAULT(false, false, false),
		PROTECTED(true, false, false),
		PROTECTED_STRIP_FINAL(true,false, true),
		PUBLIC(false, true, false),
		PUBLIC_STRIP_FINAL(false,true, true),
		STRIP_FINAL(false, false, true);

		private final boolean makeProtected;
		private final boolean makePublic;
		private final boolean stripFinal;

		Access(boolean makeProtected, boolean makePublic, boolean stripFinal) {
			this.makeProtected = makeProtected;
			this.makePublic = makePublic;
			this.stripFinal = stripFinal;
		}

		public Access makePublic() {
			return stripFinal ? PUBLIC_STRIP_FINAL : PUBLIC;
		}

		public Access makeProtected() {
			if (makePublic) return this;
			return stripFinal ? PROTECTED_STRIP_FINAL : PROTECTED;
		}

		public Access stripFinal() {
			if (makePublic) {
				return PUBLIC_STRIP_FINAL;
			} else if (makeProtected) {
				return PROTECTED_STRIP_FINAL;
			}
			return STRIP_FINAL;
		}

		public int apply(int access) {
			if (makePublic) {
				access = (access & ~7) | Opcodes.ACC_PUBLIC;
			} else if (makeProtected) {
				if ((access & Opcodes.ACC_PUBLIC) == 0) {
					//Only make it protected if not public
					access = (access & ~7) | Opcodes.ACC_PROTECTED;
				}
			}

			if (stripFinal) {
				access = access & ~Opcodes.ACC_FINAL;;
			}

			return access;
		}
	}
}
