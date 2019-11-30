package net.fabricmc.loom.task.fernflower;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Splitter;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private static TinyTree readTiny(File input) {
		try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}

	private final String namespace;

	private final Map<String, ClassDef> classes = new HashMap<>();
	private final Map<EntryTriple, FieldDef> fields = new HashMap<>();
	private final Map<EntryTriple, MethodDef> methods = new HashMap<>();

	public TinyJavadocProvider(File input) {
		this.namespace = "named";

		TinyTree mapping = readTiny(input);

		for (ClassDef classDef : mapping.getClasses()) {
			String className = classDef.getName(namespace);
			classes.put(className, classDef);

			for (FieldDef fieldDef : classDef.getFields()) {
				fields.put(new EntryTriple(className, fieldDef.getName(namespace), fieldDef.getDescriptor(namespace)), fieldDef);
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				methods.put(new EntryTriple(className, methodDef.getName(namespace), methodDef.getDescriptor(namespace)), methodDef);
			}
		}
	}

	private static Iterable<String> asLines(String doc) {
		if (doc == null) {
			return null;
		}

		return Splitter.on('\n').split(doc);
	}

	@Override
	public Iterable<String> getClassDoc(StructClass structClass) {
		ClassDef classDef = classes.get(structClass.qualifiedName);

		if (classDef == null) {
			return null;
		}

		return asLines(classDef.getComment());
	}

	@Override
	public Iterable<String> getFieldDoc(StructClass structClass, StructField structField) {
		FieldDef fieldDef = fields.get(new EntryTriple(structClass.qualifiedName, structField.getName(), structField.getDescriptor()));

		if (fieldDef == null) {
			return null;
		}

		return asLines(fieldDef.getComment());
	}

	@Override
	public Iterable<String> getMethodDoc(StructClass structClass, StructMethod structMethod) {
		MethodDef methodDef = methods.get(new EntryTriple(structClass.qualifiedName, structMethod.getName(), structMethod.getDescriptor()));

		if (methodDef == null || methodDef.getComment() == null) {
			return null;
		}

		ArrayList<String> doc = new ArrayList<>();

		for (String line : asLines(methodDef.getComment())) {
			doc.add(line);
		}

		for (ParameterDef param : methodDef.getParameters()) {
			String comment = param.getComment();

			if (comment != null) {
				doc.add(String.format("@param %s %s", param.getName(namespace), comment));
			}
		}

		return doc;
	}
}
