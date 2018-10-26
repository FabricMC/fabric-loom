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

package net.fabricmc.loom.task;


import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MapJarsTask extends DefaultTask {


	@TaskAction
	public void mapJars() throws IOException {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		if (!Constants.MINECRAFT_MAPPED_JAR.get(extension).exists() || extension.localMappings || true) {
			if(Constants.MINECRAFT_MAPPED_JAR.get(extension).exists()){
				Constants.MINECRAFT_MAPPED_JAR.get(extension).delete();
			}
			if(!extension.hasPomf()){
				this.getLogger().lifecycle("POMF version not set, skipping mapping!");
				FileUtils.copyFile(Constants.MINECRAFT_MIXED_JAR.get(extension), Constants.MINECRAFT_MAPPED_JAR.get(extension));
				return;
			}

			String fromM = "mojang";
			String toM = "pomf";

			Path mappings = Constants.MAPPINGS_TINY.get(extension).toPath();
			Path[] classpath = getProject().getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES).getFiles().stream()
					.map(File::toPath)
					.toArray(Path[]::new);

			this.getLogger().lifecycle(":remapping minecraft to " + toM);

			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
					.build();

			try {
				OutputConsumerPath outputConsumer = new OutputConsumerPath(Constants.MINECRAFT_MAPPED_JAR.get(extension).toPath());
				//Rebof the mixed mc jar
				outputConsumer.addNonClassFiles(Constants.MINECRAFT_MIXED_JAR.get(extension).toPath());
				remapper.read(Constants.MINECRAFT_MIXED_JAR.get(extension).toPath());
				remapper.read(classpath);
				remapper.apply(Constants.MINECRAFT_MIXED_JAR.get(extension).toPath(), outputConsumer);
				outputConsumer.finish();
				remapper.finish();
			} catch (Exception e){
				remapper.finish();
				throw new RuntimeException("Failed to remap minecraft to " + toM, e);
			}

			File tempAssests = new File(Constants.CACHE_FILES, "tempAssets");
			if (tempAssests.exists()) {
				FileUtils.deleteDirectory(tempAssests);
			}
			tempAssests.mkdir();

			ZipUtil.unpack(Constants.MINECRAFT_CLIENT_JAR.get(extension), tempAssests, name -> {
				if (name.startsWith("assets") || name.startsWith("pack.mcmeta") || name.startsWith("data") || name.toLowerCase().startsWith("log4j2") || name.startsWith("pack.png")) {
					return name;
				} else {
					return null;
				}
			});
			ZipUtil.unpack(Constants.MINECRAFT_MAPPED_JAR.get(extension), tempAssests);

			ZipUtil.pack(tempAssests, Constants.MINECRAFT_MAPPED_JAR.get(extension));
			FileUtils.deleteDirectory(tempAssests);
		} else {
			this.getLogger().lifecycle(Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath());
			this.getLogger().lifecycle(":mapped jar found, skipping mapping");
		}
	}
}
