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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.tinyremapper.TinyRemapper;

public class InterfaceInjectionProcessor implements JarProcessor {
	// Filename used to store hash of injected interfaces in processed jar file
	private static final String HASH_FILENAME = "injected_interfaces.sha256";

	private final Map<String, List<InjectedInterface>> injectedInterfaces;
	private final Project project;
	private final LoomGradleExtension extension;
	private final byte[] inputHash;
	private Map<String, List<InjectedInterface>> remappedInjectedInterfaces;

	public InterfaceInjectionProcessor(Project project) {
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
		this.injectedInterfaces = getInjectedInterfaces().stream()
				.collect(Collectors.groupingBy(InjectedInterface::className));

		this.inputHash = hashInjectedInterfaces();
	}

	public boolean isEmpty() {
		return injectedInterfaces.isEmpty();
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

	@Override
	public boolean isInvalid(File file) {
		byte[] hash;

		try {
			hash = ZipUtils.unpackNullable(file.toPath(), HASH_FILENAME);
		} catch (IOException e) {
			return true;
		}

		if (hash == null) {
			return true;
		}

		return !Arrays.equals(inputHash, hash);
	}

	private List<InjectedInterface> getInjectedInterfaces() {
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
			byte[] modJsonBytes;

			try {
				modJsonBytes = ZipUtils.unpackNullable(modJarPath, "fabric.mod.json");
			} catch (IOException e) {
				throw new RuntimeException("Failed to extract fabric.mod.json from " + modJarPath);
			}

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
			tinyRemapper.readClassPath(extension.getMinecraftMappedProvider().getIntermediaryJar().toPath());

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
