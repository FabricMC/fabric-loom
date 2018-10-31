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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ModProccessor {

	public static void handleMod(File input, File output, Project project){
		if(output.exists()){
			output.delete();
		}
		remapJar(input, output, project);

		JsonObject jsonObject = readInstallerJson(input);
		if(jsonObject != null){
			handleInstallerJson(jsonObject, project);
		}
	}

	private static void remapJar(File input, File output, Project project){
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		String fromM = "intermediary";
		String toM = "pomf";

		Path mappings = Constants.MAPPINGS_TINY.get(extension).toPath();
		Path[] classpath = project.getConfigurations().getByName(Constants.CONFIG_MC_DEPENDENCIES).getFiles().stream()
			.map(File::toPath)
			.toArray(Path[]::new);

		project.getLogger().lifecycle(":remapping " + input.getName() + " (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
			.build();

		try {
			OutputConsumerPath outputConsumer = new OutputConsumerPath(Paths.get(output.getAbsolutePath()));
			outputConsumer.addNonClassFiles(input.toPath());
			remapper.read(input.toPath());
			remapper.read(classpath);
			remapper.apply(input.toPath(), outputConsumer);
			outputConsumer.finish();
			remapper.finish();
		} catch (Exception e){
			remapper.finish();
			throw new RuntimeException("Failed to remap jar to " + toM, e);
		}
		if(!output.exists()){
			throw new RuntimeException("Failed to remap jar to " + toM + " file not found: " + output.getAbsolutePath());
		}
	}

	private static void handleInstallerJson(JsonObject jsonObject, Project project){
		DependencyHandler dependencyHandler = project.getDependencies();

		JsonObject libraries = jsonObject.get("libraries").getAsJsonObject();
		libraries.get("common").getAsJsonArray().forEach(jsonElement -> {
			String name = jsonElement.getAsJsonObject().get("name").getAsString();
			dependencyHandler.add("compile", name);

			//TODO is it an issue if we add the same url twice? or do I need to check this?
			if(jsonElement.getAsJsonObject().has("url")){
				project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonElement.getAsJsonObject().get("url").getAsString()));
			}

		});
	}

	private static JsonObject readInstallerJson(File file){
		try {
			JarFile jarFile = new JarFile(file);
			ZipEntry entry = jarFile.getEntry("fabric-installer.json");
			if(entry == null){
				return null;
			}
			InputStream inputstream = jarFile.getInputStream(entry);
			String jsonStr = IOUtils.toString(inputstream, StandardCharsets.UTF_8);
			inputstream.close();
			jarFile.close();

			JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
			return jsonObject;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
