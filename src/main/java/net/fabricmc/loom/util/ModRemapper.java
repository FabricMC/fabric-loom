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
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModRemapper {

	public static void remap(Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		//TODO whats the proper way of doing this???
		File libsDir = new File(project.getBuildDir(), "libs");
		File modJar = new File(libsDir, project.getName() + "-" + project.getVersion() + "-unmapped.jar");
		File modMappedJar = new File(libsDir, project.getName() + "-" + project.getVersion() + ".jar");
		if (!modMappedJar.exists()) {
			project.getLogger().error("Could not find mod jar @" + modJar.getAbsolutePath());
			return;
		}
		if (modJar.exists()) {
			modJar.delete();
		}

		modMappedJar.renameTo(modJar);

		Path mappings = Constants.MAPPINGS_TINY.get(extension).toPath();

		String fromM = "pomf";
		String toM = "mojang";

		List<File> classpathFiles = new ArrayList<>();
		classpathFiles.addAll(project.getConfigurations().getByName("compile").getFiles());
		classpathFiles.addAll(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES_CLIENT).getFiles());
		classpathFiles.addAll(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES).getFiles());
		classpathFiles.add(new File(Constants.MINECRAFT_FINAL_JAR.get(extension).getAbsolutePath()));//Seems to fix it not finding it

		Path[] classpath = new Path[classpathFiles.size()];
		for (int i = 0; i < classpathFiles.size(); i++) {
			classpath[i] = classpathFiles.get(i).toPath();
		}

		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
			.withMappings(TinyUtils.createTinyMappingProvider(Constants.MAPPINGS_MIXIN_EXPORT.get(extension).toPath(), fromM, toM))
			.build();

		OutputConsumerPath outputConsumer = new OutputConsumerPath(modMappedJar.toPath());
		outputConsumer.addNonClassFiles(modJar.toPath());
		remapper.read(modJar.toPath());
		remapper.read(classpath);
		remapper.apply(modJar.toPath(), outputConsumer);
		outputConsumer.finish();
		remapper.finish();
	}
}
