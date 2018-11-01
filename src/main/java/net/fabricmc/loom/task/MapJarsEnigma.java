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


/* import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.TranslatingTypeLoader;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.mapping.TranslationDirection;
import cuchaz.enigma.mapping.Translator;
import cuchaz.enigma.mapping.entry.ReferencedEntryPool;
import cuchaz.enigma.throwables.MappingParseException; */
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
import java.util.jar.JarFile;

public class MapJarsEnigma {
	public void mapJars(MapJarsTask task) {
		throw new RuntimeException("Currently unsupported!");
	}

	/* Deobfuscator deobfuscator;

	public void mapJars(MapJarsTask task) throws IOException, MappingParseException {
		LoomGradleExtension extension = task.getProject().getExtensions().getByType(LoomGradleExtension.class);
		if (!Constants.MINECRAFT_MAPPED_JAR.get(extension).exists() || extension.localMappings || true) {
			if(Constants.MINECRAFT_MAPPED_JAR.get(extension).exists()){
				Constants.MINECRAFT_MAPPED_JAR.get(extension).delete();
			}

			if (!extension.hasPomf()) {
				task.getLogger().lifecycle("POMF version not set, skipping mapping!");
				FileUtils.copyFile(Constants.MINECRAFT_MERGED_JAR.get(extension), Constants.MINECRAFT_MAPPED_JAR.get(extension));
				return;
			}

			if (!Constants.MAPPINGS_ENIGMA_DIR.get(extension).exists() || extension.localMappings) {
				task.getLogger().lifecycle(":unpacking mappings");
				FileUtils.deleteDirectory(Constants.MAPPINGS_ENIGMA_DIR.get(extension));
				ZipUtil.unpack(Constants.MAPPINGS_ENIGMA_ZIP.get(extension), Constants.MAPPINGS_ENIGMA_DIR.get(extension));
			}

			task.getLogger().lifecycle(":remapping jar (Enigma)");
			deobfuscator = new Deobfuscator(new JarFile(Constants.MINECRAFT_MERGED_JAR.get(extension)));
			deobfuscator.setMappings(new MappingsEnigmaReader().read(Constants.MAPPINGS_ENIGMA_DIR.get(extension)));
			writeJar(Constants.MINECRAFT_PARTIAL_ENIGMA_JAR.get(extension), new ProgressListener(), deobfuscator);

			File tempAssets = new File(Constants.CACHE_FILES, "tempAssets");
			if (tempAssets.exists()) {
				FileUtils.deleteDirectory(tempAssets);
			}
			tempAssets.mkdir();

			ZipUtil.unpack(Constants.MINECRAFT_CLIENT_JAR.get(extension), tempAssets, name -> {
				if (!name.endsWith(".class") && !name.startsWith("META-INF")) {
					return name;
				} else {
					return null;
				}
			});
			ZipUtil.unpack(Constants.MINECRAFT_PARTIAL_ENIGMA_JAR.get(extension), tempAssets);

			ZipUtil.pack(tempAssets, Constants.MINECRAFT_MAPPED_JAR.get(extension));
			FileUtils.deleteDirectory(tempAssets);
		} else {
			task.getLogger().lifecycle(Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath());
			task.getLogger().lifecycle(":mapped jar found, skipping mapping");
		}
	}

	public void writeJar(File out, Deobfuscator.ProgressListener progress, Deobfuscator deobfuscator) {
		Translator obfuscationTranslator = deobfuscator.getTranslator(TranslationDirection.OBFUSCATING);
		Translator deobfuscationTranslator = deobfuscator.getTranslator(TranslationDirection.DEOBFUSCATING);
		TranslatingTypeLoader loader = new TranslatingTypeLoader(deobfuscator.getJar(), deobfuscator.getJarIndex(), new ReferencedEntryPool(), obfuscationTranslator, deobfuscationTranslator);
		deobfuscator.transformJar(out, progress, loader::transformInto);
	}

	public static class ProgressListener implements Deobfuscator.ProgressListener {
		@Override
		public void init(int i, String s) {

		}

		@Override
		public void onProgress(int i, String s) {

		}
	} */
}
