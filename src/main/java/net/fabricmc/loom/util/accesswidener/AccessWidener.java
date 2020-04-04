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

package net.fabricmc.loom.util.accesswidener;

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
import java.util.function.IntUnaryOperator;

import org.objectweb.asm.Opcodes;

import net.fabricmc.mappings.EntryTriple;

public class AccessWidener {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, Access> methodAccess = new HashMap<>();
	public Map<EntryTriple, Access> fieldAccess = new HashMap<>();
	private Set<String> classes = new LinkedHashSet<>();

	public void read(BufferedReader reader) throws IOException {
		String[] header = reader.readLine().split("\\s+");

		if (header.length != 3 || !header[0].equals("accessWidener")) {
			throw new UnsupportedOperationException("Invalid access access widener header");
		}

		if (!header[1].equals("v1")) {
			throw new RuntimeException(String.format("Unsupported access widener format (%s)", header[1]));
		}

		if (namespace != null) {
			if (!namespace.equals(header[2])) {
				throw new RuntimeException(String.format("Namespace mismatch, expected %s got %s", namespace, header[2]));
			}
		}

		namespace = header[2];

		String line;

		Set<String> targets = new LinkedHashSet<>();

		while ((line = reader.readLine()) != null) {
			//Comment handling
			int commentPos = line.indexOf('#');

			if (commentPos >= 0) {
				line = line.substring(0, commentPos).trim();
			}

			if (line.isEmpty()) continue;

			String[] split = line.split("\\s+");

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

				classAccess.put(split[2], applyAccess(access, classAccess.getOrDefault(split[2], ClassAccess.DEFAULT), null));
				break;
			case "field":
				if (split.length != 5) {
					throw new RuntimeException(String.format("Expected (<access>\tfield\t<className>\t<fieldName>\t<fieldDesc>) got (%s)", line));
				}

				addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access, FieldAccess.DEFAULT);
				break;
			case "method":
				if (split.length != 5) {
					throw new RuntimeException(String.format("Expected (<access>\tmethod\t<className>\t<methodName>\t<methodDesc>) got (%s)", line));
				}

				addOrMerge(methodAccess, new EntryTriple(split[2], split[3], split[4]), access, MethodAccess.DEFAULT);
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
		writer.write("accessWidener\tv1\t");
		writer.write(namespace);
		writer.write("\n");

		for (Map.Entry<String, Access> entry : classAccess.entrySet()) {
			for (String s : getAccesses(entry.getValue())) {
				writer.write(s);
				writer.write("\tclass\t");
				writer.write(entry.getKey());
				writer.write("\n");
			}
		}

		for (Map.Entry<EntryTriple, Access> entry : methodAccess.entrySet()) {
			writeEntry(writer, "method", entry.getKey(), entry.getValue());
		}

		for (Map.Entry<EntryTriple, Access> entry : fieldAccess.entrySet()) {
			writeEntry(writer, "field", entry.getKey(), entry.getValue());
		}
	}

	private void writeEntry(StringWriter writer, String type, EntryTriple entryTriple, Access access) {
		for (String s : getAccesses(access)) {
			writer.write(s);
			writer.write("\t");
			writer.write(type);
			writer.write("\t");
			writer.write(entryTriple.getOwner());
			writer.write("\t");
			writer.write(entryTriple.getName());
			writer.write("\t");
			writer.write(entryTriple.getDesc());
			writer.write("\n");
		}
	}

	private List<String> getAccesses(Access access) {
		List<String> accesses = new ArrayList<>();

		if (access == ClassAccess.ACCESSIBLE || access == MethodAccess.ACCESSIBLE || access == FieldAccess.ACCESSIBLE || access == MethodAccess.ACCESSIBLE_EXTENDABLE || access == ClassAccess.ACCESSIBLE_EXTENDABLE || access == FieldAccess.ACCESSIBLE_MUTABLE) {
			accesses.add("accessible");
		}

		if (access == ClassAccess.EXTENDABLE || access == MethodAccess.EXTENDABLE || access == MethodAccess.ACCESSIBLE_EXTENDABLE || access == ClassAccess.ACCESSIBLE_EXTENDABLE) {
			accesses.add("extendable");
		}

		if (access == FieldAccess.MUTABLE || access == FieldAccess.ACCESSIBLE_MUTABLE) {
			accesses.add("mutable");
		}

		return accesses;
	}

	void addOrMerge(Map<EntryTriple, Access> map, EntryTriple entry, Access access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		Access merged = null;

		if (access instanceof ClassAccess) {
			merged = ClassAccess.DEFAULT;
		} else if (access instanceof MethodAccess) {
			merged = MethodAccess.DEFAULT;
		} else if (access instanceof FieldAccess) {
			merged = FieldAccess.DEFAULT;
		}

		merged = mergeAccess(merged, access);

		map.put(entry, merged);
	}

	void addOrMerge(Map<EntryTriple, Access> map, EntryTriple entry, String access, Access defaultAccess) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		map.put(entry, applyAccess(access, map.getOrDefault(entry, defaultAccess), entry));
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

	private Access applyAccess(String input, Access access, EntryTriple entryTriple) {
		switch (input.toLowerCase(Locale.ROOT)) {
		case "accessible":
			makeClassAccessible(entryTriple);
			return access.makeAccessible();
		case "extendable":
			makeClassExtendable(entryTriple);
			return access.makeExtendable();
		case "mutable":
			return access.makeMutable();
		default:
			throw new UnsupportedOperationException("Unknown access type:" + input);
		}
	}

	private void makeClassAccessible(EntryTriple entryTriple) {
		if (entryTriple == null) return;
		classAccess.put(entryTriple.getOwner(), applyAccess("accessible", classAccess.getOrDefault(entryTriple.getOwner(), ClassAccess.DEFAULT), null));
	}

	private void makeClassExtendable(EntryTriple entryTriple) {
		if (entryTriple == null) return;
		classAccess.put(entryTriple.getOwner(), applyAccess("extendable", classAccess.getOrDefault(entryTriple.getOwner(), ClassAccess.DEFAULT), null));
	}

	private static Access mergeAccess(Access a, Access b) {
		Access access = a;

		if (b == ClassAccess.ACCESSIBLE || b == MethodAccess.ACCESSIBLE || b == FieldAccess.ACCESSIBLE || b == MethodAccess.ACCESSIBLE_EXTENDABLE || b == ClassAccess.ACCESSIBLE_EXTENDABLE || b == FieldAccess.ACCESSIBLE_MUTABLE) {
			access = access.makeAccessible();
		}

		if (b == ClassAccess.EXTENDABLE || b == MethodAccess.EXTENDABLE || b == MethodAccess.ACCESSIBLE_EXTENDABLE || b == ClassAccess.ACCESSIBLE_EXTENDABLE) {
			access = access.makeExtendable();
		}

		if (b == FieldAccess.MUTABLE || b == FieldAccess.ACCESSIBLE_MUTABLE) {
			access = access.makeMutable();
		}

		return access;
	}

	public Access getClassAccess(String className) {
		return classAccess.getOrDefault(className, ClassAccess.DEFAULT);
	}

	public Access getFieldAccess(EntryTriple entryTriple) {
		return fieldAccess.getOrDefault(entryTriple, FieldAccess.DEFAULT);
	}

	public Access getMethodAccess(EntryTriple entryTriple) {
		return methodAccess.getOrDefault(entryTriple, MethodAccess.DEFAULT);
	}

	public Set<String> getTargets() {
		return classes;
	}

	private static int makePublic(int i) {
		return (i & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
	}

	private static int makeProtected(int i) {
		if ((i & Opcodes.ACC_PUBLIC) != 0) {
			//Return i if public
			return i;
		}

		return (i & ~(Opcodes.ACC_PRIVATE)) | Opcodes.ACC_PROTECTED;
	}

	private static int makeFinalIfPrivate(int i) {
		if ((i & Opcodes.ACC_PRIVATE) != 0) {
			return i | Opcodes.ACC_FINAL;
		}

		return i;
	}

	private static int removeFinal(int i) {
		return i & ~Opcodes.ACC_FINAL;
	}

	public interface Access extends IntUnaryOperator {
		Access makeAccessible();

		Access makeExtendable();

		Access makeMutable();
	}

	public enum ClassAccess implements Access {
		DEFAULT(i -> i),
		ACCESSIBLE(i -> makePublic(i)),
		EXTENDABLE(i -> makePublic(removeFinal(i))),
		ACCESSIBLE_EXTENDABLE(i -> makePublic(removeFinal(i)));

		private final IntUnaryOperator operator;

		ClassAccess(IntUnaryOperator operator) {
			this.operator = operator;
		}

		@Override
		public Access makeAccessible() {
			if (this == EXTENDABLE || this == ACCESSIBLE_EXTENDABLE) {
				return ACCESSIBLE_EXTENDABLE;
			}

			return ACCESSIBLE;
		}

		@Override
		public Access makeExtendable() {
			if (this == ACCESSIBLE || this == ACCESSIBLE_EXTENDABLE) {
				return ACCESSIBLE_EXTENDABLE;
			}

			return EXTENDABLE;
		}

		@Override
		public Access makeMutable() {
			throw new UnsupportedOperationException("Classes cannot be made mutable");
		}

		@Override
		public int applyAsInt(int operand) {
			return operator.applyAsInt(operand);
		}
	}

	public enum MethodAccess implements Access {
		DEFAULT(i -> i),
		ACCESSIBLE(i -> makePublic(makeFinalIfPrivate(i))),
		EXTENDABLE(i -> makeProtected(removeFinal(i))),
		ACCESSIBLE_EXTENDABLE(i -> makePublic(removeFinal(i)));

		private final IntUnaryOperator operator;

		MethodAccess(IntUnaryOperator operator) {
			this.operator = operator;
		}

		@Override
		public Access makeAccessible() {
			if (this == EXTENDABLE || this == ACCESSIBLE_EXTENDABLE) {
				return ACCESSIBLE_EXTENDABLE;
			}

			return ACCESSIBLE;
		}

		@Override
		public Access makeExtendable() {
			if (this == ACCESSIBLE || this == ACCESSIBLE_EXTENDABLE) {
				return ACCESSIBLE_EXTENDABLE;
			}

			return EXTENDABLE;
		}

		@Override
		public Access makeMutable() {
			throw new UnsupportedOperationException("Methods cannot be made mutable");
		}

		@Override
		public int applyAsInt(int operand) {
			return operator.applyAsInt(operand);
		}
	}

	public enum FieldAccess implements Access {
		DEFAULT(i -> i),
		ACCESSIBLE(i -> makePublic(i)),
		MUTABLE(i -> removeFinal(i)),
		ACCESSIBLE_MUTABLE(i -> makePublic(removeFinal(i)));

		private final IntUnaryOperator operator;

		FieldAccess(IntUnaryOperator operator) {
			this.operator = operator;
		}

		@Override
		public Access makeAccessible() {
			if (this == MUTABLE || this == ACCESSIBLE_MUTABLE) {
				return ACCESSIBLE_MUTABLE;
			}

			return ACCESSIBLE;
		}

		@Override
		public Access makeExtendable() {
			throw new UnsupportedOperationException("Fields cannot be made extendable");
		}

		@Override
		public Access makeMutable() {
			if (this == ACCESSIBLE || this == ACCESSIBLE_MUTABLE) {
				return ACCESSIBLE_MUTABLE;
			}

			return MUTABLE;
		}

		@Override
		public int applyAsInt(int operand) {
			return operator.applyAsInt(operand);
		}
	}
}
