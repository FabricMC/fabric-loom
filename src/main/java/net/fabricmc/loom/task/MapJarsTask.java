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

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.throwables.MappingParseException;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class MapJarsTask extends DefaultTask {

	Deobfuscator deobfuscator;

	@TaskAction
	public void mapJars() throws IOException, MappingParseException {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		this.getLogger().lifecycle(":unpacking mappings");
		if (Constants.MAPPINGS_DIR.exists()) {
			FileUtils.deleteDirectory(Constants.MAPPINGS_DIR);
		}
		ZipUtil.unpack(Constants.MAPPINGS_ZIP, Constants.MAPPINGS_DIR);

		this.getLogger().lifecycle(":remapping jar");
		deobfuscator = new Deobfuscator(new JarFile(Constants.MINECRAFT_MERGED_JAR.get(extension)));
		this.deobfuscator.setMappings(new MappingsEnigmaReader().read(new File(Constants.MAPPINGS_DIR, "pomf-master" + File.separator + "mappings")));
		this.deobfuscator.writeJar(Constants.MINECRAFT_MAPPED_JAR.get(extension), new ProgressListener());

		File tempAssests = new File(Constants.CACHE_FILES, "tempAssets");

		ZipUtil.unpack(Constants.MINECRAFT_CLIENT_JAR.get(extension), tempAssests, name -> {
			if (name.startsWith("assets") || name.startsWith("log4j2.xml")) {
				return name;
			} else {
				return null;
			}
		});
		ZipUtil.unpack(Constants.MINECRAFT_MAPPED_JAR.get(extension), tempAssests);

		ZipUtil.pack(tempAssests, Constants.MINECRAFT_MAPPED_JAR.get(extension));
	}

	public static class ProgressListener implements Deobfuscator.ProgressListener {
		@Override
		public void init(int i, String s) {

		}

		@Override
		public void onProgress(int i, String s) {

		}
	}

}
