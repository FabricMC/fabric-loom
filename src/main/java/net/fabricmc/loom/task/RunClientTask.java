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
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.OperatingSystem;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RunClientTask extends JavaExec {
	public RunClientTask() {
		setGroup("fabric");
	}

	@Override
	public void exec() {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		MinecraftVersionInfo minecraftVersionInfo = extension.getMinecraftProvider().versionInfo;
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		List<String> libs = new ArrayList<>();
		for (File file : getProject().getConfigurations().getByName("compile").getFiles()) {
			libs.add(file.getAbsolutePath());
		}
		for (File file : extension.getUnmappedMods()) {
			if (file.isFile()) {
				libs.add(file.getAbsolutePath());
			}
		}

		classpath(libs);
		args("--tweakClass", Constants.DEFAULT_FABRIC_CLIENT_TWEAKER, "--assetIndex", minecraftVersionInfo.assetIndex.getFabricId(extension.getMinecraftProvider().minecraftVersion), "--assetsDir", new File(extension.getUserCache(), "assets").getAbsolutePath());

		setWorkingDir(new File(getProject().getRootDir(), "run"));

		super.exec();
	}

	@Override
	public void setWorkingDir(File dir) {
		if(!dir.exists()){
			dir.mkdirs();
		}
		super.setWorkingDir(dir);
	}

	@Override
	public String getMain() {
		return "net.minecraft.launchwrapper.Launch";
	}

	@Override
	public List<String> getJvmArgs() {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		List<String> args = new ArrayList<>(super.getJvmArgs());
		args.add("-Dfabric.development=true");
		if(OperatingSystem.getOS().equalsIgnoreCase("osx")){
			args.add("-XstartOnFirstThread");
		}
		return args;
	}

}
