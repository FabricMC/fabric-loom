/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonFactory;
import net.fabricmc.loom.util.problem.LoomProblemReporter;
import net.fabricmc.loom.util.problem.LoomProblems;
import net.fabricmc.loom.util.problem.ProblemReportingOptions;

/**
 * Checks that injected interfaces are valid, i.e. that all their instance methods have a default implementation.
 *
 * <p>{@snippet lang=groovy :
 * tasks.register('validateInjectedInterfaces', net.fabricmc.loom.task.ValidateInjectedInterfacesTask) {
 * 	modJar = tasks.jar.flatMap { it.archiveFile }
 * 	sourceRoots.from(sourceSets.main.java.srcDirs)
 * }
 * }
 */
@DisableCachingByDefault
public abstract class ValidateInjectedInterfacesTask extends DefaultTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(ValidateInjectedInterfacesTask.class);
	private static final ProblemId ABSTRACT_METHOD_IN_INJECTED_INTERFACE = LoomProblems.problemId("abstract-method-in-injected-interface", "Abstract method in injected interface");

	/**
	 * The mod jar to check.
	 */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getModJar();

	/**
	 * A collection of source code roots where the mod jar was built from.
	 * This is used for resolving the corresponding source code files where report details are attached.
 	 */
	@InputFiles
	@PathSensitive(PathSensitivity.ABSOLUTE)
	public abstract ConfigurableFileCollection getSourceRoots();

	@Nested
	public abstract Property<ProblemReportingOptions> getProblemReportingOptions();

	@ApiStatus.Internal
	@Inject
	protected abstract Problems getProblems();

	public ValidateInjectedInterfacesTask() {
		setGroup(JavaBasePlugin.VERIFICATION_GROUP);
		getProblemReportingOptions().convention(ProblemReportingOptions.createDefault(getProject()));

		// Ignore outputs for up-to-date checks as there aren't any (so only inputs are checked)
		getOutputs().upToDateWhen(task -> true);
	}

	@TaskAction
	protected void check() throws IOException {
		List<Violation> violations = new ArrayList<>();
		Path modJar = getModJar().get().getAsFile().toPath();
		FabricModJson fabricModJson = FabricModJsonFactory.createFromZip(modJar);
		Set<String> injectedInterfaces = new HashSet<>();

		// Look for injected interfaces in fabric.mod.json "custom" section
		for (InterfaceInjectionProcessor.InjectedInterface injectedInterface : InterfaceInjectionProcessor.InjectedInterface.fromMod(fabricModJson)) {
			injectedInterfaces.add(injectedInterface.ifaceName());
		}

		try (var zip = new ZipFile(modJar.toFile())) {
			// Look for injected interfaces in class tweakers
			for (String classTweaker : fabricModJson.getClassTweakers().keySet()) {
				findInjectedInterfacesFromClassTweaker(zip, classTweaker, injectedInterfaces::add);
			}

			// Check injected interfaces
			for (String itf : injectedInterfaces) {
				ZipEntry classEntry = zip.getEntry(itf + ".class");

				if (classEntry == null) {
					LOGGER.info("Injected interface {} not found in mod jar {}, skipping validation", itf, modJar);
					continue;
				}

				try (InputStream in = zip.getInputStream(classEntry)) {
					checkInjectedInterface(in.readAllBytes(), violations::add);
				}
			}
		}

		if (!violations.isEmpty()) {
			var reporter = new LoomProblemReporter(getProblems().getReporter(), getProblemReportingOptions().get());

			for (Violation violation : violations) {
				reporter.problem(ABSTRACT_METHOD_IN_INJECTED_INTERFACE, builder -> {
					String qualifiedMethodName = "%s.%s%s".formatted(violation.itf, violation.methodName, violation.methodDesc);
					builder.contextualLabel(qualifiedMethodName);
					builder.message(qualifiedMethodName);
					builder.details("Method %s.%s%s is abstract.\nAll injected interface methods must have a default implementation.".formatted(violation.itf, violation.methodName, violation.methodDesc));
					builder.solution("Add a default implementation to the method.");

					if (violation.sourceFile != null) {
						builder.fileLocation(violation.sourceFile.toPath());
					}
				});
			}

			reporter.reportAndThrow(ABSTRACT_METHOD_IN_INJECTED_INTERFACE);
		}
	}

	private void findInjectedInterfacesFromClassTweaker(ZipFile zip, String classTweaker, Consumer<String> consumer) {
		ZipEntry ctEntry = zip.getEntry(classTweaker);
		ClassTweakerVisitor visitor = new ClassTweakerVisitor() {
			@Override
			public void visitInjectedInterface(String owner, String iface, boolean transitive) {
				// Strip generics in case we have a signature instead of a class name
				int genericsIndex = iface.indexOf('<');

				if (genericsIndex >= 0) {
					iface = iface.substring(0, genericsIndex);
				}

				consumer.accept(iface);
			}
		};

		try (InputStream in = zip.getInputStream(ctEntry)) {
			ClassTweakerReader.create(visitor).read(in.readAllBytes());
		} catch (IOException e) {
			throw ExceptionUtil.createDescriptiveWrapper(UncheckedIOException::new, "Could not read class tweaker " + classTweaker, e);
		}
	}

	private void checkInjectedInterface(byte[] classBytes, Consumer<Violation> violationConsumer) {
		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {
			private @Nullable String className;
			private @Nullable String sourceFile;

			@Override
			public void visit(int version, int access, String name, @Nullable String signature, @Nullable String superName, String @Nullable [] interfaces) {
				className = name;
			}

			@Override
			public void visitSource(@Nullable String source, @Nullable String debug) {
				if (source != null) {
					sourceFile = source;
				}
			}

			@Override
			public @Nullable MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature, String @Nullable [] exceptions) {
				if ((access & Opcodes.ACC_ABSTRACT) != 0) {
					violationConsumer.accept(new Violation(className, name,descriptor, resolveSourceFile(className, sourceFile)));
				}

				return null;
			}
		};
		new ClassReader(classBytes).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
	}

	private @Nullable File resolveSourceFile(String className, @Nullable String sourceFileName) {
		if (sourceFileName == null) {
			return null;
		}

		int slashIndex = className.lastIndexOf('/');
		String directory = slashIndex >= 0 ? className.substring(0, className.lastIndexOf('/') + 1) : "";
		String relativeSourcePath = directory + sourceFileName;

		for (File sourceRoot : getSourceRoots()) {
			File sourceFile = new File(sourceRoot, relativeSourcePath);

			if (sourceFile.exists()) {
				return sourceFile;
			}
		}

		return null;
	}

	private record Violation(String itf, String methodName, String methodDesc, @Nullable File sourceFile) {
	}
}
