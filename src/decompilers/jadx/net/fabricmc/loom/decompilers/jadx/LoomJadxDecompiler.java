/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.decompilers.jadx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jadx.api.CommentsLevel;
import jadx.api.ICodeInfo;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.impl.NoOpCodeCache;
import jadx.core.codegen.TypeGen;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.ClassNode;
import jadx.plugins.input.java.JavaInputPlugin;

import net.fabricmc.loom.decompilers.LoomInternalDecompiler;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class LoomJadxDecompiler implements LoomInternalDecompiler {
	private static JadxArgs getJadxArgs() {
		JadxArgs jadxArgs = new JadxArgs();
		jadxArgs.setCodeCache(NoOpCodeCache.INSTANCE);
		jadxArgs.setShowInconsistentCode(true);
		// jadxArgs.setInlineAnonymousClasses(false);
		// jadxArgs.setInlineMethods(false);
		jadxArgs.setSkipResources(true);
		jadxArgs.setRenameValid(false);
		jadxArgs.setRespectBytecodeAccModifiers(true);
		jadxArgs.setCommentsLevel(CommentsLevel.WARN);

		return jadxArgs;
	}

	@Override
	public void decompile(LoomInternalDecompiler.Context context) {
		System.out.println(System.getProperty("java.io.tmpdir"));
		JadxArgs jadxArgs = getJadxArgs();
		jadxArgs.setThreadsCount(context.numberOfThreads());

		List<Path> inputs = new ArrayList<>(context.libraries());
		inputs.add(context.compiledJar());

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

		try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
				JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(context.sourcesDestination()), manifest);
				Writer lineMapWriter = Files.newBufferedWriter(context.linemapDestination(), StandardCharsets.UTF_8)) {
			jadx.addCustomLoad(JavaInputPlugin.loadClassFiles(inputs));
			jadx.load();

			MappingTree tree = readMappings(context.javaDocs().toFile());
			JarEntryWriter jarEntryWriter = new JarEntryWriter(jarOutputStream);

			for (JavaClass cls : jadx.getClasses()) {
				if (cls.getClassNode().contains(AFlag.DONT_GENERATE)) {
					continue;
				}

				String clsName = internalNameOf(cls.getClassNode());

				// Add Javadocs
				addJavadocs(cls, tree.getClass(clsName));

				// Decompile
				ICodeInfo codeInfo = cls.getCodeInfo();

				if (codeInfo == null) {
					context.logger().error("Code not generated for class " + cls.getFullName());
					continue;
				}

				if (codeInfo == ICodeInfo.EMPTY) {
					continue;
				}

				// Write to JAR
				String filename = clsName + ".java";

				try {
					byte[] code = codeInfo.getCodeStr().getBytes(StandardCharsets.UTF_8);
					jarEntryWriter.write(filename, code);
				} catch (IOException e) {
					throw new RuntimeException("Unable to create archive: " + filename, e);
				}

				// Write line map
				int maxLine = 0;
				int maxLineDest = 0;
				StringBuilder builder = new StringBuilder();

				for (Map.Entry<Integer, Integer> mappingEntry : cls.getCodeInfo().getCodeMetadata().getLineMapping().entrySet()) {
					final int src = mappingEntry.getKey();
					final int dst = mappingEntry.getValue();

					maxLine = Math.max(maxLine, src);
					maxLineDest = Math.max(maxLineDest, dst);

					builder.append("\t").append(src).append("\t").append(dst).append("\n");
				}

				lineMapWriter.write(String.format(Locale.ENGLISH, "%s\t%d\t%d\n", clsName, maxLine, maxLineDest));
				lineMapWriter.write(builder.toString());
				lineMapWriter.write("\n");
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to decompile", e);
		}
	}

	private MappingTree readMappings(File input) {
		try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, "named");
			MappingReader.read(reader, nsSwitch);

			return mappingTree;
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}

	private void addJavadocs(JavaClass cls, ClassMapping clsMapping) {
		String comment;

		if (clsMapping == null) {
			return;
		}

		if ((comment = emptyToNull(clsMapping.getComment())) != null) {
			cls.getClassNode().addAttr(AType.CODE_COMMENTS, comment);
		}

		for (JavaField fld : cls.getFields()) {
			FieldMapping fldMapping = clsMapping.getField(fld.getFieldNode().getName(), TypeGen.signature(fld.getType()));

			if ((comment = emptyToNull(fldMapping.getComment())) != null) {
				fld.getFieldNode().addAttr(AType.CODE_COMMENTS, comment);
			}
		}

		for (JavaMethod mth : cls.getMethods()) {
			MethodInfo mthInfo = mth.getMethodNode().getMethodInfo();
			String mthName = mthInfo.getName();
			MethodMapping mthMapping = clsMapping.getMethod(mthName, mthInfo.getShortId().substring(mthName.length()));

			if ((comment = emptyToNull(mthMapping.getComment())) != null) {
				mth.getMethodNode().addAttr(AType.CODE_COMMENTS, comment);
			}
		}
	}

	private String internalNameOf(ClassNode cls) {
		return cls.getClassInfo().makeRawFullName().replace('.', '/');
	}

	public static String emptyToNull(String string) {
		return string.isEmpty() ? null : string;
	}
}
