/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.fabricmc.loom.util.proccessing;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.*;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.mixin.EnvironmentStateTweaker;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * The purpose of this class is to provide an utility for baking mixins from
 * mods into a JAR file at compile time to make accessing APIs provided by them
 * more intuitive in development environment.
 */
public class MixinPrebaker {
	private static class DesprinklingFieldVisitor extends FieldVisitor {
		public DesprinklingFieldVisitor(int api, FieldVisitor fv) {
			super(api, fv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingMethodVisitor extends MethodVisitor {
		public DesprinklingMethodVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingClassVisitor extends ClassVisitor {
		public DesprinklingClassVisitor(int api, ClassVisitor cv) {
			super(api, cv);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
			return new DesprinklingFieldVisitor(Opcodes.ASM5, super.visitField(access, name, desc, signature, value));
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			return new DesprinklingMethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static boolean isSprinkledAnnotation(String desc) {
		//System.out.println(desc);
		return desc.startsWith("Lorg/spongepowered/asm/mixin/transformer/meta");
	}

	// Term proposed by Mumfrey, don't blame me
	public static byte[] desprinkle(byte[] cls) {
		ClassReader reader = new ClassReader(cls);
		ClassWriter writer = new ClassWriter(0);

		reader.accept(new DesprinklingClassVisitor(Opcodes.ASM5, writer), 0);
		return writer.toByteArray();
	}

	public static final String APPLIED_MIXIN_CONFIGS_FILENAME = ".oml-applied-mixin-configs";
	public static final String MAPPINGS_FILENAME = ".oml-dev-mappings.tiny";

	public static void main(String[] args) {
		boolean hasMappingsFile = false;

		if (args.length < 3) {
			System.out.println("usage: MixinPrebaker [-m mapping-file] <input-jar> <output-jar> <mod-jars...>");
			return;
		}

		File mappingsFile = null;
		int argOffset;
		for (argOffset = 0; argOffset < args.length; argOffset++) {
			if ("-m".equals(args[argOffset])) {
				hasMappingsFile = true;
				mappingsFile = new File(args[++argOffset]);
				//TODO this is prob what was handling the mixin remmapping, this may need to be added back
				//FabricMixinBootstrap.setMappingFile();
			} else {
				break;
			}
		}

		Set<File> modFiles = new HashSet<>();
		for (int i = argOffset + 2; i < args.length; i++) {
			modFiles.add(new File(args[i]));
		}

		URLClassLoader ucl = (URLClassLoader) MixinPrebaker.class.getClassLoader();
		Launch.classLoader = new LaunchClassLoader(ucl.getURLs());
		Launch.blackboard = new HashMap<>();
		Launch.blackboard.put(MixinServiceLaunchWrapper.BLACKBOARD_KEY_TWEAKS, Collections.emptyList());

		List<JsonObject> modInfo = findModInfo(modFiles);
		List<JsonObject> mods = new ArrayList<>();
		for(JsonObject modInfoJson : modInfo){
			if(!modInfoJson.isJsonArray()){
				continue;
			}
			JsonArray jsonArray = modInfoJson.getAsJsonArray();
			for (int i = 0; i < jsonArray.size(); i++) {
				mods.add(jsonArray.get(i).getAsJsonObject());
			}
		}
		List<String> mixins = new ArrayList<>();
		for(JsonObject modObject : mods){
			mixins.addAll(findMixins(modObject.getAsJsonArray("mixins")));
			mixins.addAll(findMixins(modObject.getAsJsonArray("clientMixins")));
			mixins.addAll(findMixins(modObject.getAsJsonArray("serverMixins")));
		}
		System.out.println("Found " + mixins.size() + " mixins to pre bake");

		EnvironmentStateTweaker tweaker = new EnvironmentStateTweaker();
		tweaker.getLaunchArguments();
		tweaker.injectIntoClassLoader(Launch.classLoader);

		MixinTransformer mixinTransformer = GlobalProperties.get(GlobalProperties.Keys.TRANSFORMER);

		try {
			JarInputStream input = new JarInputStream(new FileInputStream(new File(args[argOffset + 0])));
			JarOutputStream output = new JarOutputStream(new FileOutputStream(new File(args[argOffset + 1])));
			JarEntry entry;
			while ((entry = input.getNextJarEntry()) != null) {
				if (entry.getName().equals(APPLIED_MIXIN_CONFIGS_FILENAME)) {
					continue;
				}

				if (hasMappingsFile && entry.getName().equals(MAPPINGS_FILENAME)) {
					continue;
				}

				if (entry.getName().endsWith(".class")) {
					byte[] classIn = ByteStreams.toByteArray(input);
					String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
					byte[] classOut = mixinTransformer.transformClassBytes(className, className, classIn);
					if (classIn != classOut) {
						System.out.println("Transformed " + className);
						classOut = desprinkle(classOut);
					}
					JarEntry newEntry = new JarEntry(entry.getName());
					newEntry.setComment(entry.getComment());
					newEntry.setSize(classOut.length);
					newEntry.setLastModifiedTime(FileTime.from(Instant.now()));
					output.putNextEntry(newEntry);
					output.write(classOut);
				} else {
					output.putNextEntry(entry);
					ByteStreams.copy(input, output);
				}
			}

			output.putNextEntry(new JarEntry(APPLIED_MIXIN_CONFIGS_FILENAME));
			output.write(String.join("\n", mixins).getBytes(Charsets.UTF_8));

			if (hasMappingsFile) {
				output.putNextEntry(new JarEntry(MAPPINGS_FILENAME));
				Files.copy(mappingsFile.toPath(), output);
			}

			input.close();
			output.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> findMixins(JsonArray jsonArray){
		if(jsonArray == null || jsonArray.size() == 0){
			return Collections.emptyList();
		}
		List<String> mixinList = new ArrayList<>();
		for (int i = 0; i < jsonArray.size(); i++) {
			mixinList.add(jsonArray.get(i).getAsString());
		}
		return mixinList;
	}

	private static List<JsonObject> findModInfo(Set<File> mods){
		return mods.stream().map(file -> {
			try {
				JarFile jar = new JarFile(file);
				return readModInfoFromJar(jar);
			} catch (IOException e) {
				throw new RuntimeException("Failed to mod " + file.getName(), e);
			}
		}).collect(Collectors.toList());
	}

	private static JsonObject readModInfoFromJar(@Nonnull JarFile file) throws IOException {
		Gson gson = new Gson();
		ZipEntry entry = file.getEntry("mod.json");
		if (entry == null)
			return null;
		return gson.fromJson(new InputStreamReader(file.getInputStream(entry)), JsonObject.class);
	}
}