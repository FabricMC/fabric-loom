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
import net.fabricmc.loom.util.proccessing.PreBakeMixins;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class ProcessModsTask extends DefaultTask {
	@TaskAction
	public void mix() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		Configuration configuration = getProject().getConfigurations().getByName(Constants.COMPILE_MODS);
		List<File> mods = new ArrayList<>();
		for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			getProject().getLogger().lifecycle(":found mod to mix:" + artifact.getFile().getName());
			mods.add(artifact.getFile());
		}
		if (Constants.MINECRAFT_FINAL_JAR.get(extension).exists()) {
			Constants.MINECRAFT_FINAL_JAR.get(extension).delete();
		}
		if (mods.size() == 0) {
			FileUtils.copyFile(Constants.MINECRAFT_MAPPED_JAR.get(extension), Constants.MINECRAFT_FINAL_JAR.get(extension));
		} else {
			//TODO figure out a way to specify what deps do what
			//TODO download deps when needed
			downloadRequiredDeps(extension);
			new PreBakeMixins().proccess(getProject(), extension, mods);
		}
	}

	public void downloadRequiredDeps(LoomGradleExtension extension) {
		Configuration configuration = getProject().getConfigurations().getByName(Constants.PROCESS_MODS_DEPENDENCIES);
		for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			addFile(artifact.getFile(), this);
		}
	}

	public static void addFile(File file, Object object) {
		try {
			URLClassLoader classLoader = (URLClassLoader) object.getClass().getClassLoader();
			Class urlClassLoaderClass = URLClassLoader.class;
			Method method = urlClassLoaderClass.getDeclaredMethod("addURL", URL.class);
			method.setAccessible(true);
			method.invoke(classLoader, file.toURI().toURL());
		} catch (NoSuchMethodException | IllegalAccessException | MalformedURLException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}
}
