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

package net.fabricmc.loom.util;

import net.fabricmc.loom.util.delayed.DelayedFile;
import net.fabricmc.loom.util.delayed.IDelayed;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class Constants {
	public static final File WORKING_DIRECTORY = new File(".");
	public static final IDelayed<File> PROJECT_CACHE_DIR = new DelayedFile(extension -> extension.getProject().getGradle().getStartParameter().getProjectCacheDir());
	public static final IDelayed<File> CACHE_FILES = new DelayedFile(extension -> new File(extension.getProject().getGradle().getStartParameter().getProjectCacheDir(), "minecraft"));

	public static final String JAR_MAPPER_ENIGMA = "enigma";
	public static final String JAR_MAPPER_TINY = "tiny";

	public static final IDelayed<File> MINECRAFT_CLIENT_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-client.jar"));
	public static final IDelayed<File> MINECRAFT_SERVER_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-server.jar"));
	public static final IDelayed<File> MINECRAFT_MERGED_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-merged.jar"));
	public static final IDelayed<File> MINECRAFT_MAPPED_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-mapped-" + extension.pomfVersion + ".jar"));
	public static final IDelayed<File> MINECRAFT_INTERMEDIARY_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-intermediary.jar"));
//	public static final IDelayed<File> MINECRAFT_PARTIAL_ENIGMA_JAR = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-partial-enigma-" + extension.pomfVersion + ".jar"));
//	public static final IDelayed<File> MINECRAFT_FINAL_JAR = new DelayedFile(extension -> new File(CACHE_FILES, extension.version + "-final-" + extension.pomfVersion + ".jar"));
	public static final IDelayed<File> MINECRAFT_FINAL_JAR = MINECRAFT_MAPPED_JAR;

	public static final IDelayed<File> POMF_DIR = new DelayedFile(extension -> new File(extension.getUserCache(), "pomf"));
	public static       IDelayed<File> MAPPINGS_TINY_GZ = new DelayedFile(extension -> new File(POMF_DIR.get(extension), "pomf-tiny-" + extension.version + "." + extension.pomfVersion + ".gz"));
	public static final IDelayed<File> MAPPINGS_TINY = new DelayedFile(extension -> new File(POMF_DIR.get(extension), "pomf-tiny-" + extension.version + "." + extension.pomfVersion));
	public static final IDelayed<File> MAPPINGS_MIXIN_EXPORT = new DelayedFile(extension -> new File(CACHE_FILES.get(extension), "mixin-map-" + extension.version + "." + extension.pomfVersion + ".tiny"));
	public static       IDelayed<File> MAPPINGS_ENIGMA_ZIP = new DelayedFile(extension -> new File(POMF_DIR.get(extension), "pomf-enigma-" + extension.version + "." + extension.pomfVersion + ".zip"));
	public static final IDelayed<File> MAPPINGS_ENIGMA_DIR = new DelayedFile(extension -> new File(POMF_DIR.get(extension), "pomf-enigma-" + extension.version + "." + extension.pomfVersion + ""));

	public static final IDelayed<File> MAPPINGS_DIR_LOCAL = new DelayedFile(extension -> new File(WORKING_DIRECTORY, "mappings"));
	public static final IDelayed<File> MAPPINGS_ENIGMA_ZIP_LOCAL = new DelayedFile(extension -> new File(MAPPINGS_DIR_LOCAL.get(extension), "pomf-enigma-" + extension.version + ".zip"));
	public static final IDelayed<File> MAPPINGS_TINY_GZ_LOCAL = new DelayedFile(extension -> new File(MAPPINGS_DIR_LOCAL.get(extension), "pomf-tiny-" + extension.version + ".gz"));

	public static final IDelayed<File> REMAPPED_MODS_STORE = new DelayedFile(extension -> new File(CACHE_FILES.get(extension), "remapped_mods"));

	public static final IDelayed<File> MINECRAFT_LIBS = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-libs"));
	public static final IDelayed<File> MINECRAFT_NATIVES = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-natives"));
	public static final IDelayed<File> MINECRAFT_JSON = new DelayedFile(extension -> new File(extension.getUserCache(), extension.version + "-info.json"));

	public static final IDelayed<File> VERSION_MANIFEST = new DelayedFile(extension -> new File(extension.getUserCache(), "version_manifest.json"));

	public static final String FABRIC_CLIENT_TWEAKER = "net.fabricmc.loader.launch.FabricClientTweaker";
	public static final String FABRIC_SERVER_TWEAKER = "net.fabricmc.loader.launch.FabricServerTweaker";
	public static final String POMF_MAVEN_SERVER = "https://maven.modmuss50.me/net/fabricmc/pomf/";

	public static final String LIBRARIES_BASE = "https://libraries.minecraft.net/";
	public static final String RESOURCES_BASE = "http://resources.download.minecraft.net/";

	public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";

	public static final String CONFIG_NATIVES = "MC_NATIVES";
	public static final String CONFIG_MC_DEPENDENCIES = "MC_DEPENDENCIES";
	public static final String CONFIG_MINECRAFT = "MINECRAFT";
	public static final String CONFIG_MC_DEPENDENCIES_CLIENT = "MC_DEPENDENCIES_CLIENT";
	public static final String SYSTEM_ARCH = System.getProperty("os.arch").equals("64") ? "64" : "32";
	public static final String COMPILE_MODS = "modCompile";

	public static List<String> getClassPath() {
		URL[] urls = ((URLClassLoader) Constants.class.getClassLoader()).getURLs();
		ArrayList<String> list = new ArrayList<>();
		for (URL url : urls) {
			list.add(url.getPath());
		}
		return list;
	}
}
