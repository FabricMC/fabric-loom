/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.tool;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.LoomProblems;
import net.fabricmc.mappingio.FlatMappingVisitor;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.FlatAsRegularMappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

/**
 * Checks that the mod-provided javadoc mappings in a specific file are valid.
 * This task can be used together with {@link ModEnigmaTask} to author and check
 * mod-provided javadoc files.
 */
@DisableCachingByDefault
public abstract class ValidateModProvidedJavadocTask extends AbstractLoomTask {
	private static final ProblemId MAPPINGS_CONTAIN_DST_NAMES = LoomProblems.problemId("mappings-contain-dst-names", "Mod-provided javadoc mapping file contains renames");
	private static final ProblemId INCORRECT_MAPPING_SRC_NAME = LoomProblems.problemId("incorrect-mapping-src-name", "Mod-provided javadoc mapping file has an invalid source namespace");
	private static final ProblemId MAPPING_PARSING_ERROR = LoomProblems.problemId("mapping-parsing-error", "Cannot parse mod-provided javadoc mapping file");
	private static final ProblemId CODE_ELEMENT_MISSING = LoomProblems.problemId("code-element-missing", "Cannot find code element to document");

	/**
	 * The mapping files to check.
	 */
	@InputFiles
	@SkipWhenEmpty
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getMappingFiles();

	/**
	 * The game jars. All targeted code elements will need to be in these jars.
	 */
	@Classpath
	public abstract ConfigurableFileCollection getMinecraftJars();

	/**
	 * The expected source namespaced for the mappings.
	 */
	@Input
	public abstract Property<String> getExpectedNamespace();

	@ApiStatus.Internal
	@Inject
	protected abstract Problems getProblems();

	public ValidateModProvidedJavadocTask() {
		getMinecraftJars().convention(getExtension().getProductionNamespaceEnum().map(getExtension()::getMinecraftJarsCollection));
		getExpectedNamespace().convention(getExtension().getProductionNamespace());

		// Ignore outputs for up-to-date checks as there aren't any (so only inputs are checked)
		getOutputs().upToDateWhen(task -> true);
	}

	@TaskAction
	protected void check() throws IOException {
		try (var validator = new Validator(this::reportError, getMinecraftJars().getFiles())) {
			for (File mappingFile : getMappingFiles()) {
				validator.check(mappingFile.toPath(), getExpectedNamespace().get());
			}
		}
	}

	private void reportError(ProblemId problemId, Path currentPath, @Nullable String details, @Nullable Exception cause) throws IOException {
		final ProblemReporter reporter = getProblems().getReporter();
		final String message = details != null ? details : problemId.getDisplayName();
		reporter.throwing(new IOException(message, cause), problemId,
				spec -> {
					spec.fileLocation(currentPath.toAbsolutePath().toString());

					if (details != null) {
						spec.details(details);
					}

					if (cause != null) {
						spec.withException(cause);
					}
				});
	}

	@VisibleForTesting
	@FunctionalInterface
	public interface ErrorReporter {
		void reportError(ProblemId problemId, Path currentPath, @Nullable String details, @Nullable Exception cause) throws IOException;

		default void reportError(ProblemId problemId, Path currentPath, @Nullable String details) throws IOException {
			reportError(problemId, currentPath, details, null);
		}
	}

	@VisibleForTesting
	public static final class Validator implements Closeable {
		private final ErrorReporter errorReporter;
		private final GameJarIndex jarIndex;

		public Validator(ErrorReporter errorReporter, Collection<File> targetJars) throws IOException {
			this.errorReporter = errorReporter;
			this.jarIndex = new GameJarIndex(targetJars);
		}

		public void check(Path mappingFile, String expectedNamespace) throws IOException {
			try {
				final var structuralChecker = new StructuralChecker(jarIndex, errorReporter, mappingFile);
				final MappingVisitor visitor = new MappingChecker(new FlatAsRegularMappingVisitor(structuralChecker), expectedNamespace, errorReporter, mappingFile);
				MappingReader.read(mappingFile, visitor);
			} catch (IOException e) {
				errorReporter.reportError(MAPPING_PARSING_ERROR, mappingFile, "Cannot parse mappings, " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
			}
		}

		@Override
		public void close() throws IOException {
			jarIndex.close();
		}
	}

	private static final class GameJarIndex implements Closeable {
		private final List<FileSystemUtil.Delegate> fileSystems;
		private final Map<String, ClassNode> classNodes = new HashMap<>();

		private GameJarIndex(Collection<File> files) throws IOException {
			fileSystems = new ArrayList<>(files.size());

			for (File file : files) {
				fileSystems.add(FileSystemUtil.getJarFileSystem(file.toPath()));
			}
		}

		public boolean classExists(String className) throws IOException {
			if (classNodes.containsKey(className)) {
				return true;
			}

			for (FileSystemUtil.Delegate fileSystem : fileSystems) {
				final Path classFile = fileSystem.getPath(className + ".class");

				if (Files.exists(classFile)) {
					final ClassNode node = new ClassNode();
					final ClassReader cr = new ClassReader(Files.readAllBytes(classFile));
					cr.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					classNodes.put(className, node);
					return true;
				}
			}

			return false;
		}

		public boolean fieldExists(String owner, String name, @Nullable String desc) throws IOException {
			if (desc == null || !classExists(owner)) return false;

			for (FieldNode field : classNodes.get(owner).fields) {
				if (name.equals(field.name) && desc.equals(field.desc)) {
					return true;
				}
			}

			return false;
		}

		public boolean methodExists(String owner, String name, @Nullable String desc) throws IOException {
			if (desc == null || !classExists(owner)) return false;

			for (MethodNode method : classNodes.get(owner).methods) {
				if (name.equals(method.name) && desc.equals(method.desc)) {
					return true;
				}
			}

			return false;
		}

		@Override
		public void close() throws IOException {
			@Nullable IOException suppressed = null;

			for (FileSystemUtil.Delegate fs : fileSystems) {
				try {
					fs.close();
				} catch (IOException e) {
					if (suppressed != null) {
						suppressed.addSuppressed(e);
					} else {
						suppressed = e;
					}
				}
			}

			if (suppressed != null) {
				throw suppressed;
			}
		}
	}

	private static final class MappingChecker extends ForwardingMappingVisitor {
		private final String expectedNamespace;
		private final ErrorReporter errorReporter;
		private final Path currentPath;

		private MappingChecker(MappingVisitor next, String expectedNamespace, ErrorReporter errorReporter, Path currentPath) {
			super(next);
			this.expectedNamespace = expectedNamespace;
			this.errorReporter = errorReporter;
			this.currentPath = currentPath;
		}

		@Override
		public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
			if (!expectedNamespace.equals(srcNamespace) && !MappingUtil.NS_SOURCE_FALLBACK.equals(srcNamespace)) {
				errorReporter.reportError(INCORRECT_MAPPING_SRC_NAME, currentPath, "Expected %s or %s for the source namespace, but found %s.".formatted(expectedNamespace, MappingUtil.NS_SOURCE_FALLBACK, srcNamespace));
			}

			super.visitNamespaces(srcNamespace, dstNamespaces);
		}

		@Override
		public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
			errorReporter.reportError(MAPPINGS_CONTAIN_DST_NAMES, currentPath, "These mappings cannot contain any destination names. They can only contain javadoc.");
		}
	}

	private static final class StructuralChecker implements FlatMappingVisitor {
		private final GameJarIndex jarIndex;
		private final ErrorReporter errorReporter;
		private final Path currentPath;

		private StructuralChecker(GameJarIndex jarIndex, ErrorReporter errorReporter, Path currentPath) {
			this.jarIndex = jarIndex;
			this.errorReporter = errorReporter;
			this.currentPath = currentPath;
		}

		@Override
		public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		}

		@Override
		public boolean visitClass(String srcName, @Nullable String @Nullable [] dstNames) {
			return true;
		}

		@Override
		public void visitClassComment(String srcName, @Nullable String @Nullable [] dstNames, String comment) throws IOException {
			if (!jarIndex.classExists(srcName)) {
				errorReporter.reportError(CODE_ELEMENT_MISSING, currentPath, "Class " + srcName + " does not exist");
			}
		}

		@Override
		public boolean visitField(String srcClsName, String srcName, @Nullable String srcDesc, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstNames, @Nullable String @Nullable [] dstDescs) {
			return true;
		}

		@Override
		public void visitFieldComment(String srcClsName, String srcName, @Nullable String srcDesc, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstNames, @Nullable String @Nullable [] dstDescs, String comment) throws IOException {
			if (!jarIndex.fieldExists(srcClsName, srcName, srcDesc)) {
				errorReporter.reportError(CODE_ELEMENT_MISSING, currentPath, "Field %s.%s:%s does not exist".formatted(srcClsName, srcName, srcDesc));
			}
		}

		@Override
		public boolean visitMethod(String srcClsName, String srcName, @Nullable String srcDesc, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstNames, @Nullable String @Nullable [] dstDescs) {
			return true;
		}

		@Override
		public void visitMethodComment(String srcClsName, String srcName, @Nullable String srcDesc, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstNames, @Nullable String @Nullable [] dstDescs, String comment) throws IOException {
			if (!jarIndex.methodExists(srcClsName, srcName, srcDesc)) {
				errorReporter.reportError(CODE_ELEMENT_MISSING, currentPath, "Method %s.%s%s does not exist".formatted(srcClsName, srcName, srcDesc));
			}
		}

		@Override
		public boolean visitMethodArg(String srcClsName, String srcMethodName, @Nullable String srcMethodDesc, int argPosition, int lvIndex, @Nullable String srcName, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstMethodNames, @Nullable String @Nullable [] dstMethodDescs, String[] dstNames) {
			return true;
		}

		@Override
		public void visitMethodArgComment(String srcClsName, String srcMethodName, @Nullable String srcMethodDesc, int argPosition, int lvIndex, @Nullable String srcName, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstMethodNames, @Nullable String @Nullable [] dstMethodDescs, @Nullable String @Nullable [] dstNames, String comment) throws IOException {
			if (!jarIndex.methodExists(srcClsName, srcMethodName, srcMethodDesc)) {
				errorReporter.reportError(CODE_ELEMENT_MISSING, currentPath, "Method %s.%s%s does not exist".formatted(srcClsName, srcMethodName, srcMethodDesc));
			}
		}

		@Override
		public boolean visitMethodVar(String srcClsName, String srcMethodName, @Nullable String srcMethodDesc, int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstMethodNames, @Nullable String @Nullable [] dstMethodDescs, String[] dstNames) {
			return true;
		}

		@Override
		public void visitMethodVarComment(String srcClsName, String srcMethodName, @Nullable String srcMethodDesc, int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName, @Nullable String @Nullable [] dstClsNames, @Nullable String @Nullable [] dstMethodNames, @Nullable String @Nullable [] dstMethodDescs, @Nullable String @Nullable [] dstNames, String comment) throws IOException {
			if (!jarIndex.methodExists(srcClsName, srcMethodName, srcMethodDesc)) {
				errorReporter.reportError(CODE_ELEMENT_MISSING, currentPath, "Method %s.%s%s does not exist".formatted(srcClsName, srcMethodName, srcMethodDesc));
			}
		}
	}
}
