/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.providers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.launch.Launch;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class LaunchProvider extends DependencyProvider {
	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) {
		if (extension.getLoaderLaunchMethod().equals("launchwrapper")) {
			return;
		}

		postPopulationScheduler.accept(() -> {
			File jarFile = new File(extension.getRootProjectPersistentCache(), "LaunchClasses.jar");

			if (jarFile.exists()) {
				//TODO possibly check the jar to see if it does need changing? some sort of hash?
				boolean success = jarFile.delete();
				if (!success) throw new RuntimeException("Failed to delete launchclasses.jar, is the game currently running?");
			}

			final LaunchDetails clientLaunchDetails = new LaunchDetails(getMainClass("client", extension));
			final LaunchDetails serverLaunchDetails = new LaunchDetails(getMainClass("server", extension));

			clientLaunchDetails
					.property("fabric.development", "true")
					.property("java.library.path", extension.getNativesDirectory().getAbsolutePath())
					.property("org.lwjgl.librarypath", extension.getNativesDirectory().getAbsolutePath())
					.argument("--assetIndex")
					.argument(extension.getMinecraftProvider().versionInfo.assetIndex.getFabricId(extension.getMinecraftProvider().minecraftVersion))
					.argument("--assetsDir")
					.argument(new File(extension.getUserCache(), "assets").getAbsolutePath());

			serverLaunchDetails
					.property("fabric.development", "true");

			final List<ZipEntrySource> entries = new ArrayList<>();
			entries.add(generateZipEntry("net/fabricmc/launch/LaunchClient", clientLaunchDetails));
			entries.add(generateZipEntry("net/fabricmc/launch/LaunchServer", serverLaunchDetails));

			ZipUtil.pack(entries.toArray(new ZipEntrySource[0]), jarFile);

			addDependency(jarFile, project);
		});
	}

	private static ZipEntrySource generateZipEntry(String className, LaunchDetails launchDetails) {
		return new ByteSource(className + ".class", generateClass(className, launchDetails));
	}

	private static byte[] generateClass(String className, LaunchDetails launchDetails) {
		final ClassNode classNode = readLaunchClass();

		//No need for the constructor
		classNode.methods.removeIf(mn -> mn.name.equals("<init>"));
		classNode.name = className;

		//Find the main method
		MethodNode method = classNode.methods.stream()
				.filter(mn -> mn.name.equals("main"))
				.findFirst().orElse(null);

		patchMethod(method, launchDetails);

		return writeClassToBytes(classNode);
	}

	private static void patchMethod(MethodNode method, LaunchDetails launchDetails) {
		final InsnList insnList = new InsnList();

		for (Map.Entry<String, String> entry : launchDetails.getSystemProperties().entrySet()) {
			setProperty(insnList, entry.getKey(), entry.getValue());
		}

		for (String arg : launchDetails.getArgs()) {
			addArg(insnList, arg);
		}

		setMainClass(method, launchDetails.getMainClass());
		injectInsnLists(method, insnList);
	}

	private static void setProperty(InsnList insnList, String key, String value) {
		insnList.add(new LdcInsnNode(key));
		insnList.add(new LdcInsnNode(value));
		insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
		insnList.add(new InsnNode(Opcodes.POP));
	}

	private static void addArg(InsnList insnList, String arg) {
		insnList.add(new VarInsnNode(Opcodes.ALOAD, 1)); //Just going to assume this is always 1 for now
		insnList.add(new LdcInsnNode(arg));
		insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
		insnList.add(new InsnNode(Opcodes.POP));
	}

	private static void setMainClass(MethodNode methodNode, String mainClass) {
		for (AbstractInsnNode node : methodNode.instructions.toArray()) {
			if (node.getOpcode() == Opcodes.INVOKESTATIC && node instanceof MethodInsnNode) {
				MethodInsnNode methodInsnNode = (MethodInsnNode) node;

				if (methodInsnNode.name.equals("main")) {
					methodInsnNode.owner = mainClass;
					return;
				}
			}
		}
	}

	private static void injectInsnLists(MethodNode method, InsnList insnList) {
		//Find the ASTORE inst, only one for the ArrayList
		AbstractInsnNode targetNode = Arrays.stream(method.instructions.toArray())
				.filter(abstractInsnNode -> abstractInsnNode.getOpcode() == Opcodes.ASTORE)
				.findFirst().orElse(null);

		method.instructions.insert(targetNode, insnList);
	}

	private static ClassNode readLaunchClass() {
		return readClassFromBytes(getClassBytes(Launch.class));
	}

	private static byte[] getClassBytes(final Class clazz) {
		byte[] arr;

		try (InputStream is = clazz.getClassLoader().getResourceAsStream(clazz.getName().replaceAll("\\.", "/") + ".class")) {
			arr = ByteStreams.toByteArray(Objects.requireNonNull(is));
		} catch (IOException | NullPointerException e) {
			throw new RuntimeException("Failed to read " + clazz.getName(), e);
		}

		return arr;
	}

	private static ClassNode readClassFromBytes(byte[] bytes) {
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		return classNode;
	}

	private static byte[] writeClassToBytes(ClassNode classNode) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(writer);
		return writer.toByteArray();
	}

	//This can be removed at somepoint, its not ideal but its the best solution I could thing of
	public static boolean needsUpgrade(File file) throws IOException {
		String contents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		return !(contents.contains("net.fabricmc.launch.LaunchClient") || contents.contains("net.fabricmc.launch.LaunchServer"));
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT_NAMED;
	}

	private static String getMainClass(String side, LoomGradleExtension extension) {
		JsonObject installerJson = extension.getInstallerJson();

		if (installerJson != null) {
			if (installerJson.has("mainClass")) {
				JsonElement mainClassJson = installerJson.get("mainClass");

				String mainClassName = "";

				if (mainClassJson.isJsonObject()) {
					JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

					if (mainClassesJson.has(side)) {
						mainClassName = mainClassesJson.get(side).getAsString();
					}
				} else {
					mainClassName = mainClassJson.getAsString();
				}

				return mainClassName.replaceAll("\\.", "/");
			}
		}

		throw new RuntimeException("Failed to find mainclass");
	}

	public static class LaunchDetails {
		private final String mainClass;

		private final Map<String, String> systemProperties = new HashMap<>();
		private final List<String> args = new ArrayList<>();

		public LaunchDetails(String mainClass) {
			this.mainClass = mainClass;
		}

		public LaunchDetails property(String key, String value) {
			systemProperties.put(key, value);
			return this;
		}

		public LaunchDetails argument(String value) {
			args.add(value);
			return this;
		}

		public String getMainClass() {
			return mainClass;
		}

		public Map<String, String> getSystemProperties() {
			return systemProperties;
		}

		public List<String> getArgs() {
			return args;
		}
	}
}
