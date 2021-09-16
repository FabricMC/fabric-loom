/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.configuration.ifaceinject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Constants;

public class InterfaceInjectionProcessor implements JarProcessor {
	private final Map<String, List<InjectedInterface>> injectedInterfaces;
	private final Project project;
	private final LoomGradleExtension extension;

	public InterfaceInjectionProcessor(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
		this.injectedInterfaces = getInjectedInterface().stream()
				.collect(Collectors.groupingBy(InjectedInterface::className));
	}

	public boolean isEmpty() {
		return injectedInterfaces.isEmpty();
	}

	@Override
	public void setup() {
	}

	@Override
	public void process(File jarFile) {
		project.getLogger().lifecycle("Processing file: " + jarFile.getName());
		ZipUtil.transformEntries(jarFile, getTransformers());
	}

	private ZipEntryTransformerEntry[] getTransformers() {
		return injectedInterfaces.keySet().stream()
				.map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);
				ClassVisitor classVisitor = new InjectingClassVisitor(Constants.ASM_VERSION, writer, injectedInterfaces.get(className));

				project.getLogger().info("Applying access widener to " + className);

				reader.accept(classVisitor, 0);
				return writer.toByteArray();
			}
		};
	}

	@Override
	public boolean isInvalid(File file) {
		return false;
	}

	private List<InjectedInterface> getInjectedInterface() {
		List<InjectedInterface> result = new ArrayList<>();

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			// Only apply injected interfaces from mods that are part of the compile classpath
			if (!entry.compileClasspath()) {
				continue;
			}

			Set<File> artifacts = extension.getLazyConfigurationProvider(entry.sourceConfiguration())
					.get()
					.resolve();

			for (File artifact : artifacts) {
				result.addAll(InjectedInterface.fromModJar(artifact.toPath()));
			}
		}

		return result;
	}

	private record InjectedInterface(String modId, String className, String ifaceName) {
		/**
		 * Reads the injected interfaces contained in a mod jar, or returns null if there is none.
		 */
		public static List<InjectedInterface> fromModJar(Path modJarPath) {
			byte[] modJsonBytes = ZipUtil.unpackEntry(modJarPath.toFile(), "fabric.mod.json");

			if (modJsonBytes == null) {
				return Collections.emptyList();
			}

			JsonObject jsonObject = new Gson().fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

			String modId = jsonObject.get("id").getAsString();

			if (!jsonObject.has("custom")) {
				return Collections.emptyList();
			}

			JsonObject custom = jsonObject.getAsJsonObject("custom");

			if (!custom.has("loom:injected_interfaces")) {
				return Collections.emptyList();
			}

			JsonObject addedIfaces = custom.getAsJsonObject("loom:injected_interfaces");

			List<InjectedInterface> result = new ArrayList<>();

			for (String className : addedIfaces.keySet()) {
				JsonArray ifaceNames = addedIfaces.getAsJsonArray(className);

				for (JsonElement ifaceName : ifaceNames) {
					result.add(new InjectedInterface(modId, className, ifaceName.getAsString()));
				}
			}

			return result;
		}
	}

	private static class InjectingClassVisitor extends ClassVisitor {
		private final List<InjectedInterface> injectedInterfaces;

		InjectingClassVisitor(int asmVersion, ClassWriter writer, List<InjectedInterface> injectedInterfaces) {
			super(asmVersion, writer);
			this.injectedInterfaces = injectedInterfaces;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			Set<String> modifiedInterfaces = new LinkedHashSet<>(interfaces.length + injectedInterfaces.size());
			Collections.addAll(modifiedInterfaces, interfaces);

			for (InjectedInterface injectedInterface : injectedInterfaces) {
				modifiedInterfaces.add(injectedInterface.ifaceName());
			}

			super.visit(version, access, name, signature, superName, modifiedInterfaces.toArray(new String[0]));
		}
	}
}
