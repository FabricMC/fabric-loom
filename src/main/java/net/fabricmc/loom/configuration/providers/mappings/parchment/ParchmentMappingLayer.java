/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.parchment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;

import net.fabricmc.mappingio.tree.VisitableMappingTree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ParchmentMappingLayer(Path parchmentFile, boolean removePrefix) implements MappingLayer {
	private static final String PARCHMENT_DATA_FILE_NAME = "parchment.json";
	private static final Logger LOGGER = LoggerFactory.getLogger(ParchmentMappingLayer.class);

	@Override
	public void visit(VisitableMappingTree mappingTree) throws IOException {
		ParchmentTreeV1 parchmentData = getParchmentData();
		
		// Hack to allow classes marked @DontObfuscate to be mapped
		Stats stats = new Stats();
		assert parchmentData.classes() != null;
		for (ParchmentTreeV1.Class clazz : parchmentData.classes()) {
			if (mappingTree.getClass(clazz.name()) == null) {
				mappingTree.addClass(new ClassEntry(mappingTree, clazz));
				stats.unobfuscatedCount++;
			}
		}
		LOGGER.info(
				"Remapped {} unobfuscated classes with Parchment",
				stats.unobfuscatedCount
		);

		MappingVisitor mappingVisitor = mappingTree;
		if (removePrefix()) {
			mappingVisitor = new ParchmentPrefixStripingMappingVisitor(mappingTree);
		}

		parchmentData.visit(mappingVisitor, MappingsNamespace.NAMED.toString());
	}

	private ParchmentTreeV1 getParchmentData() throws IOException {
		return ZipUtils.unpackJson(parchmentFile, PARCHMENT_DATA_FILE_NAME, ParchmentTreeV1.class);
	}
	
	private static final class Stats {
		public int unobfuscatedCount;
	}
	
	private static final class FieldEntry implements MappingTree.FieldMapping {
		private final MappingTree tree;
		private final MappingTree.ClassMapping owner;
		private final String desc;
		private final String name;
		private final String comment;

		private FieldEntry(
				MappingTree tree,
				MappingTree.ClassMapping owner,
				String desc,
				String name,
				List<String> javadoc
		) {
			this.tree = tree;
			this.owner = owner;
			this.desc = desc;
			this.name = name;
			if (javadoc != null && !javadoc.isEmpty()) {
				this.comment = String.join("\n", javadoc);
			} else {
				this.comment = null;
			}
		}

		@Override
		public MappingTree.ClassMapping getOwner() {
			return owner;
		}

		@Override
		public @Nullable String getSrcDesc() {
			return desc;
		}

		@Override
		public MappingTree getTree() {
			return tree;
		}

		@Override
		public String getSrcName() {
			return name;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return name;
		}

		@Override
		public @Nullable String getComment() {
			return comment;
		}

		@Override
		public void setSrcDesc(String desc) {
		}

		@Override
		public void setDstName(String name, int namespace) {
		}

		@Override
		public void setComment(String comment) {
		}
	}
	
	private static final class MethodArgEntry implements MappingTree.MethodArgMapping {
		private final MappingTree tree;
		private MethodEntry method;
		private final int argPosition;
		private final int lvIndex;
		private final String name;

		private MethodArgEntry(
				MappingTree tree,
				int argPosition,
				int lvIndex,
				String name
		) {
			this.tree = tree;
			this.argPosition = argPosition;
			this.lvIndex = lvIndex;
			this.name = name;
		}
		
		public void setMethod(MethodEntry method) {
			this.method = method;
		}

		@Override
		public MappingTree.MethodMapping getMethod() {
			return method;
		}

		@Override
		public int getArgPosition() {
			return argPosition;
		}

		@Override
		public int getLvIndex() {
			return lvIndex;
		}

		@Override
		public MappingTree getTree() {
			return tree;
		}

		@Override
		public String getSrcName() {
			return name;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return name;
		}

		@Override
		public @NotNull String getComment() {
			return "";
		}

		@Override
		public void setArgPosition(int position) {
		}

		@Override
		public void setLvIndex(int index) {
		}

		@Override
		public void setDstName(String name, int namespace) {
		}

		@Override
		public void setComment(String comment) {
		}
	}

	private static final class MethodEntry implements MappingTree.MethodMapping {
		private final MappingTree tree;
		private final ClassEntry owner;
		private final List<MethodArgEntry> args;
		private final String name;
		private final String desc;
		private final @Nullable String comment;

		private MethodEntry(
				MappingTree tree,
				ClassEntry owner,
				List<MethodArgEntry> args,
				String name,
				String desc,
				List<String> javadoc
		) {
			this.tree = tree;
			this.owner = owner;
			this.args = args;
			this.name = name;
			this.desc = desc;
			if (javadoc != null && !javadoc.isEmpty()) {
				this.comment = String.join("\n", javadoc);
			} else {
				this.comment = null;
			}
			
			for (MethodArgEntry arg : args) {
				arg.setMethod(this);
			}
		}

		@Override
		public Collection<? extends MappingTree.MethodArgMapping> getArgs() {
			return args;
		}

		// stolen from MemoryMappingTree#getArg
		@Override
		public MappingTree.@Nullable MethodArgMapping getArg(
				int argPosition,
				int lvIndex,
				@Nullable String srcName
		) {
			if (args == null) return null;

			if (argPosition >= 0 || lvIndex >= 0) {
				for (MethodArgEntry entry : args) {
					if (argPosition >= 0 && entry.argPosition == argPosition
							|| lvIndex >= 0 && entry.lvIndex == lvIndex) {
						if (srcName != null && entry.getSrcName() != null && !srcName.equals(
								entry.getSrcName()))
							continue; // both srcNames are present but not equal
						return entry;
					}
				}
			}

			if (srcName != null) {
				for (MethodArgEntry entry : args) {
					if (srcName.equals(entry.getSrcName())
							&& (argPosition < 0 || entry.argPosition < 0)
							&& (lvIndex < 0 || entry.lvIndex < 0)) {
						return entry;
					}
				}
			}

			return null;
		}

		@Override
		public MappingTree.MethodArgMapping addArg(MappingTree.MethodArgMapping arg) {
			return null;
		}

		@Override
		public MappingTree.@Nullable MethodArgMapping removeArg(
				int argPosition,
				int lvIndex,
				@Nullable String srcName
		) {
			return null;
		}

		@Override
		public Collection<? extends MappingTree.MethodVarMapping> getVars() {
			return List.of();
		}

		@Override
		public MappingTree.@Nullable MethodVarMapping getVar(
				int lvtRowIndex,
				int lvIndex,
				int startOpIdx,
				int endOpIdx,
				@Nullable String srcName
		) {
			return null;
		}

		@Override
		public MappingTree.MethodVarMapping addVar(MappingTree.MethodVarMapping var) {
			return null;
		}

		@Override
		public MappingTree.@Nullable MethodVarMapping removeVar(
				int lvtRowIndex,
				int lvIndex,
				int startOpIdx,
				int endOpIdx,
				@Nullable String srcName
		) {
			return null;
		}

		@Override
		public MappingTree.ClassMapping getOwner() {
			return owner;
		}

		@Override
		public @Nullable String getSrcDesc() {
			return desc;
		}

		@Override
		public MappingTree getTree() {
			return tree;
		}

		@Override
		public String getSrcName() {
			return name;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return name;
		}

		@Override
		public @Nullable String getComment() {
			return comment;
		}

		@Override
		public void setSrcDesc(String desc) {
		}

		@Override
		public void setDstName(String name, int namespace) {
		}

		@Override
		public void setComment(String comment) {
		}
	}
	
	private static final class ClassEntry implements MappingTree.ClassMapping {
		private final MappingTree tree;
		private final Collection<MappingTree.FieldMapping> fields = new ArrayList<>();
		private final Collection<MappingTree.MethodMapping> methods = new ArrayList<>();
		private final String name;
		private final @Nullable String comment;
		
		public ClassEntry(MappingTree tree, ParchmentTreeV1.Class cls) {
			this.tree = tree;
			this.name = cls.name();
			if (cls.javadoc() != null && !cls.javadoc().isEmpty()) {
				this.comment = String.join("\n", cls.javadoc());
			} else {
				this.comment = null;
			}
			
			if (cls.fields() != null) {
				for (ParchmentTreeV1.Field field : cls.fields()) {
					fields.add(new FieldEntry(
							tree,
							this,
							field.descriptor(),
							field.name(),
							field.javadoc()
					));
				}
			}
			
			if (cls.methods() != null) {
				for (ParchmentTreeV1.Method method : cls.methods()) {
					var args = new ArrayList<MethodArgEntry>();
					if (method.parameters() != null) {
						for (ParchmentTreeV1.Parameter parameter : method.parameters()) {
							args.add(new MethodArgEntry(
									tree,
									parameter.index(),
									parameter.index(),
									parameter.name()
							));
						}
					}
					
					methods.add(new MethodEntry(
							tree,
							this,
							args,
							method.name(),
							method.descriptor(),
							method.javadoc()
					));
				}
			}
		}
		
		@Override
		public Collection<? extends MappingTree.FieldMapping> getFields() {
			return fields;
		}

		@Override
		public MappingTree.@Nullable FieldMapping getField(
				String srcName,
				@Nullable String srcDesc
		) {
			for (MappingTree.FieldMapping field : fields) {
				if (srcName.equals(field.getSrcName())) {
					return field;
				}
			}
			
			return null;
		}

		@Override
		public MappingTree.FieldMapping addField(MappingTree.FieldMapping field) {
			return null;
		}

		@Override
		public MappingTree.@Nullable FieldMapping removeField(
				String srcName,
				@Nullable String srcDesc
		) {
			return null;
		}

		@Override
		public Collection<? extends MappingTree.MethodMapping> getMethods() {
			return methods;
		}

		@Override
		public MappingTree.@Nullable MethodMapping getMethod(
				String srcName,
				@Nullable String srcDesc
		) {
			for (MappingTree.MethodMapping method : methods) {
				if (method.getSrcName().equals(srcName)) {
					if (method.getSrcDesc() == null || !method.getSrcDesc().equals(srcDesc)) continue;
					
					return method;
				}
			}
			
			return null;
		}

		@Override
		public MappingTree.MethodMapping addMethod(MappingTree.MethodMapping method) {
			return null;
		}

		@Override
		public MappingTree.@Nullable MethodMapping removeMethod(
				String srcName,
				@Nullable String srcDesc
		) {
			return null;
		}

		@Override
		public MappingTree getTree() {
			return tree;
		}

		@Override
		public String getSrcName() {
			return name;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return name;
		}

		@Override
		public @Nullable String getComment() {
			return comment;
		}

		@Override
		public void setDstName(String name, int namespace) {
		}

		@Override
		public void setComment(String comment) {
		}
	}
}
