/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

/**
 * Task to validate mixin names.
 *
 * <pre>{@code
 * task validateMixinNames(type: net.fabricmc.loom.task.ValidateMixinNameTask) {
 * 		source(sourceSets.main.output)
 * 		softFailures = false
 * }
 * }</pre>
 */
public abstract class ValidateMixinNameTask extends SourceTask {
	@Input
	abstract Property<Boolean> getSoftFailures();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public ValidateMixinNameTask() {
		setGroup("verification");
		getProject().getTasks().getByName("check").dependsOn(this);
		getSoftFailures().convention(false);
	}

	@TaskAction
	public void run() {
		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(ValidateMixinAction.class, params -> {
			params.getInputClasses().from(getSource().matching(pattern -> pattern.include("**/*.class")));
			params.getSoftFailures().set(getSoftFailures());
		});
	}

	public interface ValidateMixinsParams extends WorkParameters {
		ConfigurableFileCollection getInputClasses();
		Property<Boolean> getSoftFailures();
	}

	public abstract static class ValidateMixinAction implements WorkAction<ValidateMixinsParams> {
		public static final Logger LOGGER = LoggerFactory.getLogger(ValidateMixinAction.class);

		@Override
		public void execute() {
			final Set<File> files = getParameters().getInputClasses().getAsFileTree().getFiles();
			final List<String> errors = new LinkedList<>();

			for (File file : files) {
				final Mixin mixin = getMixin(file);

				if (mixin == null) {
					continue;
				}

				final String mixinClassName = toSimpleName(mixin.className);
				final String expectedMixinClassName = mixin.expectedClassName();

				if (expectedMixinClassName.startsWith("class_")) {
					// Don't enforce intermediary named mixins.
					continue;
				}

				if (!expectedMixinClassName.equals(mixinClassName)) {
					errors.add("%s -> %s".formatted(mixin.className, expectedMixinClassName));
				}
			}

			if (errors.isEmpty()) {
				return;
			}

			final String message = "Mixin name validation failed: " + errors.stream().collect(Collectors.joining(System.lineSeparator()));

			if (getParameters().getSoftFailures().get()) {
				LOGGER.warn(message);
				return;
			}

			throw new GradleException("Mixin name validation failed: " + errors.stream().collect(Collectors.joining(System.lineSeparator())));
		}
	}

	private static String toSimpleName(String internalName) {
		return internalName.substring(internalName.lastIndexOf("/") + 1);
	}

	@VisibleForTesting
	public record Mixin(String className, Type target, boolean accessor) {
		public String expectedClassName() {
			return toSimpleName(target.getInternalName()).replace("$", "") + (accessor ? "Accessor" : "Mixin");
		}
	}

	@Nullable
	private static Mixin getMixin(File file) {
		try (InputStream is = new FileInputStream(file)) {
			return getMixin(is);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read input file: " + file, e);
		}
	}

	@Nullable
	@VisibleForTesting
	public static Mixin getMixin(InputStream is) throws IOException {
		final ClassReader reader = new ClassReader(is);

		var classVisitor = new MixinTargetClassVisitor();
		reader.accept(classVisitor, ClassReader.SKIP_CODE);

		if (classVisitor.mixinTarget != null && classVisitor.targets == 1) {
			return new Mixin(classVisitor.className, classVisitor.mixinTarget, classVisitor.accessor);
		}

		return null;
	}

	private static class MixinTargetClassVisitor extends ClassVisitor {
		Type mixinTarget;
		String className;
		boolean accessor;
		int targets = 0;

		boolean isInterface;

		protected MixinTargetClassVisitor() {
			super(Constants.ASM_VERSION);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.className = name;
			this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

			if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
				av = new MixinAnnotationVisitor(av);
			}

			return av;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

			if (mixinTarget != null) {
				mv = new MixinMethodVisitor(mv);
			}

			return mv;
		}

		private class MixinAnnotationVisitor extends AnnotationVisitor {
			MixinAnnotationVisitor(AnnotationVisitor annotationVisitor) {
				super(Constants.ASM_VERSION, annotationVisitor);
			}

			@Override
			public AnnotationVisitor visitArray(String name) {
				final AnnotationVisitor av = super.visitArray(name);

				if ("value".equals(name)) {
					return new AnnotationVisitor(Constant.ASM_VERSION, av) {
						@Override
						public void visit(String name, Object value) {
							mixinTarget = Objects.requireNonNull((Type) value);
							targets++;

							super.visit(name, value);
						}
					};
				}

				return av;
			}
		}

		private class MixinMethodVisitor extends MethodVisitor {
			protected MixinMethodVisitor(MethodVisitor methodVisitor) {
				super(Constants.ASM_VERSION, methodVisitor);
			}

			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if ("Lorg/spongepowered/asm/mixin/gen/Accessor;".equals(descriptor)) {
					accessor = true;
				} else if ("Lorg/spongepowered/asm/mixin/gen/Invoker;".equals(descriptor)) {
					accessor = true;
				}

				return super.visitAnnotation(descriptor, visible);
			}
		}
	}
}
