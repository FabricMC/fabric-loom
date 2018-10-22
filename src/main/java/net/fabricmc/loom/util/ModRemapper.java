/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 FabricMC
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

package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.tinyremapper.*;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ModRemapper {

	public static void remap(Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		//TODO whats the proper way of doing this???
		File libsDir = new File(project.getBuildDir(), "libs");
		File deobfJar = new File(libsDir, project.getName() + "-" + project.getVersion() + "-deobf.jar");
		File modJar = new File(libsDir, project.getName() + "-" + project.getVersion() + ".jar");
		if (!modJar.exists()) {
			project.getLogger().error("Could not find mod jar @" + deobfJar.getAbsolutePath());
			project.getLogger().error("This is can be fixed by adding a 'settings.gradle' file specifying 'rootProject.name'");
			return;
		}
		if (deobfJar.exists()) {
			deobfJar.delete();
		}

		FileUtils.touch(modJar); //Done to ensure that the file can be moved
		//Move the pre existing mod jar to the deobf jar
		if(!modJar.renameTo(deobfJar)){
			throw new RuntimeException("Failed to rename " + modJar);
		}

		Path mappings = Constants.MAPPINGS_TINY.get(extension).toPath();

		String fromM = "pomf";
		String toM = "mojang";

		List<File> classpathFiles = new ArrayList<>();
		classpathFiles.addAll(project.getConfigurations().getByName("compile").getFiles());
		classpathFiles.addAll(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES_CLIENT).getFiles());
		classpathFiles.addAll(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES).getFiles());

		Path[] classpath = new Path[classpathFiles.size()];
		for (int i = 0; i < classpathFiles.size(); i++) {
			classpath[i] = classpathFiles.get(i).toPath();
		}

		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
			.withRemapperExtension(new MixinRemapper())
			.build();

		try {
			OutputConsumerPath outputConsumer = new OutputConsumerPath(modJar.toPath());
			//Rebof the deobf jar
			outputConsumer.addNonClassFiles(deobfJar.toPath());
			remapper.read(deobfJar.toPath());
			remapper.read(classpath);
			remapper.apply(deobfJar.toPath(), outputConsumer);
			outputConsumer.finish();
			remapper.finish();
		} catch (Exception e){
			remapper.finish();
			throw new RuntimeException("Failed to remap jar", e);
		}

		if(!deobfJar.exists() || !modJar.exists()){
			throw new RuntimeException("Failed to rebof jar");
		}

		//Add the deobf jar to be uploaded to maven
		project.getArtifacts().add("archives", deobfJar);
	}

	public static class MixinRemapper implements IRemapperExtension{

		@Override
		public byte[] handleUnmappedClass(byte[] inBytes, TinyRemapper remapper) {
			//I know this isnt the fastest, but its the easiest
			ClassNode classNode = readClassFromBytes(inBytes);
			if(isMixin(classNode)){
				String target = getMixinTarget(classNode);
				System.out.println("Remapping mixin (" + classNode.name + ") targeting: " + target);
				for(MethodNode methodNode : classNode.methods){
					if(needsTargetMap(methodNode.visibleAnnotations)){
						methodNode.visibleAnnotations.add(createTargetMap(methodNode, target, remapper));
					}
				}
				for(FieldNode fieldNode : classNode.fields){
					if(needsTargetMap(fieldNode.visibleAnnotations)){
						//fieldNode.visibleAnnotations.add(createTargetMap(fieldNode));
					}
				}
				return writeClassToBytes(classNode);
			}
			return inBytes;
		}

		private AnnotationNode createTargetMap(MethodNode methodNode, String targetClass, TinyRemapper remapper){
			AnnotationNode targetMapNode = new AnnotationNode("Lme/modmuss50/fusion/api/TargetMap;");
			String deobfTarget = methodNode.name + methodNode.desc;
			if(getRewriteTarget(methodNode).isPresent()){
				deobfTarget = getRewriteTarget(methodNode).get();
			}

			if(deobfTarget.equals("<init>")){
				//No need to handle constructors, may need to do something about the desc but we will see
				return targetMapNode;
			}
			String oldName = deobfTarget.substring(0, deobfTarget.indexOf("("));
			String oldDesc = deobfTarget.substring(deobfTarget.lastIndexOf("("));

			String newName = remapper.remapper.mapMethodName(targetClass.replaceAll("\\.", "/"), oldName, oldDesc);
			String newDesc = remapper.remapper.mapDesc(oldDesc);

			System.out.println(oldName + oldDesc + " -> " + newName + newDesc);
			targetMapNode.visit("value", newName + newDesc);
			return targetMapNode;
		}

		private boolean isMixin(ClassNode classNode){
			if(classNode.visibleAnnotations == null){
				return false;
			}
			for(AnnotationNode annotation : classNode.visibleAnnotations){
				if(annotation.desc.equals("Lme/modmuss50/fusion/api/Mixin;")){
					return true;
				}
			}
			return false;
		}

		private String getMixinTarget(ClassNode classNode){
			if(classNode.visibleAnnotations == null){
				throw new RuntimeException(classNode.name + " is not a mixin!");
			}
			for(AnnotationNode annotation : classNode.visibleAnnotations){
				if(annotation.desc.equals("Lme/modmuss50/fusion/api/Mixin;")){
					for (int i = 0; i < annotation.values.size(); i++) {
						Object value = annotation.values.get(i);
						if(value instanceof String && value.toString().equals("value")){
							Type target = (Type) annotation.values.get(i + 1);
							return target.getClassName();
						}
					}

				}
			}
			throw new RuntimeException(classNode.name + " is not a valid mixin!");
		}

		private Optional<String> getRewriteTarget(MethodNode methodNode){
			if(methodNode.visibleAnnotations == null){
				return Optional.empty();
			}
			for(AnnotationNode annotation : methodNode.visibleAnnotations){
				if(annotation.desc.equals("Lme/modmuss50/fusion/api/Rewrite;")){
					for (int i = 0; i < annotation.values.size(); i++) {
						Object value = annotation.values.get(i);
						if(value instanceof String && value.toString().equals("target")){
							return Optional.of((String) annotation.values.get(i + 1));
						}
					}
				}
			}
			return Optional.empty();
		}

		private boolean needsTargetMap(List<AnnotationNode> annotationNodes){
			if(annotationNodes == null){
				return false;
			}
			for(AnnotationNode annotation : annotationNodes){
				if(annotation.desc.equals("Lme/modmuss50/fusion/api/Rewrite;")){
					return true;
				}
				if(annotation.desc.equals("Lme/modmuss50/fusion/api/Inject;")){
					return true;
				}
			}
			return false;
		}


		private static ClassNode readClassFromBytes(byte[] bytes) {
			ClassNode classNode = new org.objectweb.asm.tree.ClassNode();
			ClassReader classReader = new ClassReader(bytes);
			classReader.accept(classNode, 0);
			return classNode;
		}

		private static byte[] writeClassToBytes(ClassNode classNode) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			classNode.accept(writer);
			return writer.toByteArray();
		}

	}


}
