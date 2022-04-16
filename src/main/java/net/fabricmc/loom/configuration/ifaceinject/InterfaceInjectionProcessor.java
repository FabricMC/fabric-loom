/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.InterfaceInjectionExtensionAPI;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ModUtils;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;

public class InterfaceInjectionProcessor implements JarProcessor, GenerateSourcesTask.MappingsProcessor {
	// Filename used to store hash of injected interfaces in processed jar file
	private static final String HASH_FILENAME = "injected_interfaces.sha256";

	private final Map<String, List<InjectedInterface>> injectedInterfaces;
	private final Project project;
	private final LoomGradleExtension extension;
	private final InterfaceInjectionExtensionAPI interfaceInjectionExtension;
	private final byte[] inputHash;
	private Map<String, List<InjectedInterface>> remappedInjectedInterfaces;

	public InterfaceInjectionProcessor(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
		this.interfaceInjectionExtension = this.extension.getInterfaceInjection();
		this.injectedInterfaces = getInjectedInterfaces().stream()
				.collect(Collectors.groupingBy(InjectedInterface::className));

		this.inputHash = hashInjectedInterfaces();
	}

	public boolean isEmpty() {
		return injectedInterfaces.isEmpty();
	}

	@Override
	public String getId() {
		Preconditions.checkArgument(!isEmpty());
		return "loom:interface_injection:" + Checksum.toHex(inputHash);
	}

	@Override
	public void setup() {
	}

	@Override
	public void process(File jarFile) {
		// Lazily remap from intermediary->named
		if (remappedInjectedInterfaces == null) {
			TinyRemapper tinyRemapper = createTinyRemapper();
			Remapper remapper = tinyRemapper.getEnvironment().getRemapper();

			try {
				remappedInjectedInterfaces = new HashMap<>(injectedInterfaces.size());

				for (Map.Entry<String, List<InjectedInterface>> entry : injectedInterfaces.entrySet()) {
					String namedClassName = remapper.map(entry.getKey());
					remappedInjectedInterfaces.put(
							namedClassName,
							entry.getValue().stream()
									.map(injectedInterface ->
											new InjectedInterface(
													injectedInterface.modId(),
													namedClassName,
													remapper.map(injectedInterface.ifaceName())
											))
									.toList()
					);
				}
			} finally {
				tinyRemapper.finish();
			}
		}

		project.getLogger().lifecycle("Processing file: " + jarFile.getName());

		try {
			ZipUtils.transform(jarFile.toPath(), getTransformers());
		} catch (IOException e) {
			throw new RuntimeException("Failed to apply interface injections to " + jarFile, e);
		}
	}

	private List<Pair<String, ZipUtils.UnsafeUnaryOperator<byte[]>>> getTransformers() {
		return remappedInjectedInterfaces.keySet().stream()
				.map(string -> new Pair<>(string.replaceAll("\\.", "/") + ".class", getTransformer(string)))
				.collect(Collectors.toList());
	}

	private ZipUtils.UnsafeUnaryOperator<byte[]> getTransformer(String className) {
		return input -> {
			ClassReader reader = new ClassReader(input);
			ClassWriter writer = new ClassWriter(0);
			List<InjectedInterface> ifaces = remappedInjectedInterfaces.get(className);
			ClassVisitor classVisitor = new InjectingClassVisitor(Constants.ASM_VERSION, writer, ifaces);

			// Log which mods add which interface to the class
			project.getLogger().info("Injecting interfaces into " + className + ": "
					+ ifaces.stream().map(i -> i.ifaceName() + " [" + i.modId() + "]"
			).collect(Collectors.joining(", ")));

			reader.accept(classVisitor, 0);
			return writer.toByteArray();
		};
	}

	private List<InjectedInterface> getInjectedInterfaces() {
		List<InjectedInterface> result = new ArrayList<>();

		if (interfaceInjectionExtension.getEnableDependencyInterfaceInjection().get()) {
			result.addAll(getDependencyInjectedInterfaces());
		}

		for (SourceSet sourceSet : interfaceInjectionExtension.getInterfaceInjectionSourceSets().get()) {
			result.addAll(getSourceInjectedInterface(sourceSet));
		}

		return result;
	}

	private List<InjectedInterface> getDependencyInjectedInterfaces() {
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

	private List<InjectedInterface> getSourceInjectedInterface(SourceSet sourceSet) {
		final File fabricModJson;

		try {
			fabricModJson = sourceSet.getResources()
					.matching(patternFilterable -> patternFilterable.include("fabric.mod.json"))
					.getSingleFile();
		} catch (IllegalStateException e) {
			// File not found
			return Collections.emptyList();
		}

		final String jsonString;

		try {
			jsonString = Files.readString(fabricModJson.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read fabric.mod.json", e);
		}

		final JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(jsonString, JsonObject.class);

		return InjectedInterface.fromJson(jsonObject);
	}

	@Override
	public boolean transform(MemoryMappingTree mappings) {
		if (injectedInterfaces.isEmpty()) {
			return false;
		}

		if (!MappingsNamespace.INTERMEDIARY.toString().equals(mappings.getSrcNamespace())) {
			throw new IllegalStateException("Mapping tree must have intermediary src mappings not " + mappings.getSrcNamespace());
		}

		for (Map.Entry<String, List<InjectedInterface>> entry : injectedInterfaces.entrySet()) {
			final String className = entry.getKey();
			final List<InjectedInterface> injectedInterfaces = entry.getValue();

			MappingTree.ClassMapping classMapping = mappings.getClass(className);

			if (classMapping == null) {
				final String modIds = injectedInterfaces.stream().map(InjectedInterface::modId).distinct().collect(Collectors.joining(","));
				project.getLogger().warn("Failed to find class ({}) to add injected interfaces from mod(s) ({})", className, modIds);
				continue;
			}

			classMapping.setComment(appendComment(classMapping.getComment(), injectedInterfaces));
		}

		return true;
	}

	private static String appendComment(String comment, List<InjectedInterface> injectedInterfaces) {
		for (InjectedInterface injectedInterface : injectedInterfaces) {
			String iiComment = "Interface {@link %s} injected by mod %s".formatted(injectedInterface.ifaceName.substring(injectedInterface.ifaceName.lastIndexOf("/") + 1), injectedInterface.modId);

			if (comment == null || !comment.contains(iiComment)) {
				if (comment == null) {
					comment = iiComment;
				} else {
					comment += "\n" + iiComment;
				}
			}
		}

		return comment;
	}

	private record InjectedInterface(String modId, String className, String ifaceName) {
		/**
		 * Reads the injected interfaces contained in a mod jar, or returns empty if there is none.
		 */
		public static List<InjectedInterface> fromModJar(Path modJarPath) {
			final JsonObject jsonObject = ModUtils.getFabricModJson(modJarPath);

			if (jsonObject == null) {
				return Collections.emptyList();
			}

			return fromJson(jsonObject);
		}

		public static List<InjectedInterface> fromJson(JsonObject jsonObject) {
			final String modId = jsonObject.get("id").getAsString();

			if (!jsonObject.has("custom")) {
				return Collections.emptyList();
			}

			final JsonObject custom = jsonObject.getAsJsonObject("custom");

			if (!custom.has(Constants.CustomModJsonKeys.INJECTED_INTERFACE)) {
				return Collections.emptyList();
			}

			final JsonObject addedIfaces = custom.getAsJsonObject(Constants.CustomModJsonKeys.INJECTED_INTERFACE);

			final List<InjectedInterface> result = new ArrayList<>();

			for (String className : addedIfaces.keySet()) {
				final JsonArray ifaceNames = addedIfaces.getAsJsonArray(className);

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

			// See JVMS: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-ClassSignature
			if (signature != null) {
				var resultingSignature = new StringBuilder(signature);

				for (InjectedInterface injectedInterface : injectedInterfaces) {
					String superinterfaceSignature = "L" + injectedInterface.ifaceName() + ";";

					if (resultingSignature.indexOf(superinterfaceSignature) == -1) {
						resultingSignature.append(superinterfaceSignature);
					}
				}

				signature = resultingSignature.toString();
			}

			super.visit(version, access, name, signature, superName, modifiedInterfaces.toArray(new String[0]));
		}
	}

	private TinyRemapper createTinyRemapper() {
		try {
			TinyRemapper tinyRemapper = TinyRemapperHelper.getTinyRemapper(project, "intermediary", "named");
			tinyRemapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(project));

			for (Path minecraftJar : extension.getMinecraftJars(MappingsNamespace.INTERMEDIARY)) {
				tinyRemapper.readClassPath(minecraftJar);
			}

			return tinyRemapper;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create tiny remapper for intermediary->named", e);
		}
	}

	private byte[] hashInjectedInterfaces() {
		// Hash the interfaces we're about to inject to not have to repeat this everytime
		Hasher hasher = Hashing.sha256().newHasher();

		for (Map.Entry<String, List<InjectedInterface>> entry : injectedInterfaces.entrySet()) {
			hasher.putString("class:", StandardCharsets.UTF_8);
			hasher.putString(entry.getKey(), StandardCharsets.UTF_8);

			for (InjectedInterface ifaceName : entry.getValue()) {
				hasher.putString("iface:", StandardCharsets.UTF_8);
				hasher.putString(ifaceName.ifaceName(), StandardCharsets.UTF_8);
			}
		}

		return hasher.hash().asBytes();
	}
}
