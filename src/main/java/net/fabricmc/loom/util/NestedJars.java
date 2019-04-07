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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loom.task.RemapJar;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;

public class NestedJars {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static boolean addNestedJars(Project project, File modJar) {
		if (getContainedJars(project).isEmpty()) {
			return false;
		}

		ZipUtil.addEntries(modJar, getContainedJars(project).stream().map(file -> new FileSource("META-INF/jars/" + file.getName(), file)).toArray(ZipEntrySource[]::new));

		return ZipUtil.transformEntries(modJar, single(new ZipEntryTransformerEntry("fabric.mod.json", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) throws IOException {
				JsonObject json = GSON.fromJson(input, JsonObject.class);
				JsonArray nestedJars = json.getAsJsonArray("jars");
				if (nestedJars == null || !json.has("jars")) {
					nestedJars = new JsonArray();
				}

				for (File file : getContainedJars(project)) {
					JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("file", "META-INF/jars/" + file.getName());
					nestedJars.add(jsonObject);
				}

				json.add("jars", nestedJars);

				return GSON.toJson(json);
			}
		})));
	}

	private static List<File> getContainedJars(Project project) {

		List<File> fileList = new ArrayList<>();

		Configuration configuration = project.getConfigurations().getByName(Constants.INCLUDE);
		DependencySet dependencies = configuration.getDependencies();
		for (Dependency dependency : dependencies) {
			if (dependency instanceof ProjectDependency) {
				ProjectDependency projectDependency = (ProjectDependency) dependency;
				Project dependencyProject = projectDependency.getDependencyProject();

				//TODO change this to allow just normal jar tasks, so a project can have a none loom sub project
				for (Task task : dependencyProject.getTasksByName("remapJar", false)) {
					if (task instanceof RemapJar) {
						fileList.add(((RemapJar) task).jar);
					}
				}
			} else {
				fileList.addAll(configuration.files(dependency));
			}
		}
		for (File file : fileList) {
			if (!file.exists()) {
				throw new RuntimeException("Failed to include nested jars, as it could not be found @ " + file.getAbsolutePath());
			}
			if (file.isDirectory() || !file.getName().endsWith(".jar")) {
				throw new RuntimeException("Failed to include nested jars, as file was not a jar: " + file.getAbsolutePath());
			}
		}
		return fileList;
	}

	private static ZipEntryTransformerEntry[] single(ZipEntryTransformerEntry element) {
		return new ZipEntryTransformerEntry[]{element};
	}
}
