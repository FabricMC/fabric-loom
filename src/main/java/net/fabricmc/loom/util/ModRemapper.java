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
import net.fabricmc.tinyremapper.OutputConsumerJar;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class ModRemapper {

	public static void remap(Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		//TODO whats the proper way of doing this???
		File libsDir = new File(project.getBuildDir(), "libs");
		File modJar = new File(libsDir, project.getName() + "-" + project.getVersion() + ".jar");
		File modOutputJar = new File(libsDir, project.getName() + "-" + project.getVersion() + "-final.jar");
		if (!modJar.exists()) {
			project.getLogger().error("Could not find mod jar @" + modJar.getAbsolutePath());
			return;
		}
		if (!Constants.MAPPINGS_TINY.get(extension).exists()) {
			if (!Constants.MAPPINGS_TINY_GZ.get(extension).exists()) {
				project.getLogger().lifecycle(":downloading tiny mappings");
				FileUtils.copyURLToFile(new URL("http://asie.pl:8080/job/pomf/" + extension.pomfVersion + "/artifact/build/libs/pomf-tiny-" + extension.version + "." + extension.pomfVersion + ".gz"), Constants.MAPPINGS_TINY_GZ.get(extension));
			}
			GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(Constants.MAPPINGS_TINY_GZ.get(extension)));
			FileOutputStream fileOutputStream = new FileOutputStream(Constants.MAPPINGS_TINY.get(extension));
			int length;
			byte[] buffer = new byte[1024];
			while ((length = gzipInputStream.read(buffer)) > 0) {
				fileOutputStream.write(buffer, 0, length);
			}
			gzipInputStream.close();
			fileOutputStream.close();
		}

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
			.build();

		OutputConsumerJar outputConsumer = new OutputConsumerJar(modOutputJar);
		outputConsumer.addNonClassFiles(modJar);
		remapper.read(modJar.toPath());
		remapper.read(classpath);
		remapper.apply(modJar.toPath(), outputConsumer);
		outputConsumer.finish();
		remapper.finish();
	}
}
