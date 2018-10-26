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
		classpathFiles.addAll(project.getConfigurations().getByName(Constants.CONFIG_MINECRAFT).getFiles());

		Path[] classpath = new Path[classpathFiles.size()];
		for (int i = 0; i < classpathFiles.size(); i++) {
			classpath[i] = classpathFiles.get(i).toPath();
		}

		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
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

}
