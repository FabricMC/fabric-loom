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

package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.commons.FileUtils;

public class MapJarsTask extends DefaultTask {

	//Set to to true if you want to always remap the jar, useful for when working on the gradle plugin
	public static final boolean ALWAYS_REMAP = false;

	@TaskAction
	public void mapJars() throws Exception {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		if (!Constants.MINECRAFT_MAPPED_JAR.get(extension).exists() || extension.localMappings || ALWAYS_REMAP) {
			if(Constants.MINECRAFT_MAPPED_JAR.get(extension).exists()){
				Constants.MINECRAFT_MAPPED_JAR.get(extension).delete();
			}

			if (!extension.hasPomf()) {
				this.getLogger().lifecycle("Mapping version not set, skipping mapping!");
				FileUtils.copyFile(Constants.MINECRAFT_MERGED_JAR.get(extension), Constants.MINECRAFT_MAPPED_JAR.get(extension));
				return;
			}

			if (Constants.JAR_MAPPER_ENIGMA.equals(extension.jarMapper)) {
				new MapJarsEnigma().mapJars(this);
			} else if (Constants.JAR_MAPPER_TINY.equals(extension.jarMapper)) {
				new MapJarsTiny().mapJars(this);
			} else {
				throw new RuntimeException("Unknown JAR mapper type: " + extension.jarMapper);
			}
		} else {
			this.getLogger().lifecycle(Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath());
			this.getLogger().lifecycle(":mapped jar found, skipping mapping");
		}
	}
}
