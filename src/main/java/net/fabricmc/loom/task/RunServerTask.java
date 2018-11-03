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

import com.google.gson.Gson;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Version;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class RunServerTask extends JavaExec {

	@Override
	public void exec() {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		Gson gson = new Gson();
		Version version = null;
		try {
			version = gson.fromJson(new FileReader(Constants.MINECRAFT_JSON.get(extension)), Version.class);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		List<String> libs = new ArrayList<>();
		for (File file : getProject().getConfigurations().getByName("compile").getFiles()) {
			libs.add(file.getAbsolutePath());
		}
		for (File file : getProject().getConfigurations().getByName(Constants.CONFIG_MINECRAFT).getFiles()) {
			libs.add(file.getAbsolutePath());
		}
		//Used to add the fabric jar that has been built
		for (File file : new File(getProject().getBuildDir(), "libs").listFiles()) {
			if (file.isFile()) {
				libs.add(file.getAbsolutePath());
			}
		}
		libs.add(Constants.MINECRAFT_FINAL_JAR.get(extension).getAbsolutePath());
		classpath(libs);

		args("--tweakClass", Constants.FABRIC_SERVER_TWEAKER,  "--fabricMappingFile", Constants.MAPPINGS_TINY.get(extension).getAbsolutePath());

		setWorkingDir(new File(getProject().getRootDir(), "run"));

		super.exec();
	}

	@Override
	public String getMain() {
		return "net.minecraft.launchwrapper.Launch";
	}

	@Override
	public void setWorkingDir(File dir) {
		if(!dir.exists()){
			dir.mkdirs();
		}
		super.setWorkingDir(dir);
	}

	@Override
	public List<String> getJvmArgs() {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		List<String> args = new ArrayList<>();
		args.add("-Djava.library.path=" + Constants.MINECRAFT_NATIVES.get(extension).getAbsolutePath());
		args.add("-Dfabric.development=true");
		return args;
	}

}
