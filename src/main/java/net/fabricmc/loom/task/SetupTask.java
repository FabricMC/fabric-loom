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
import net.fabricmc.loom.util.ModProccessor;
import org.apache.commons.lang3.Validate;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class SetupTask extends DefaultTask {

	@TaskAction
	public void setup(){
		configureModRemapper();
	}

	public void configureModRemapper(){
		LoomGradleExtension extension = getProject().getExtensions().getByType(LoomGradleExtension.class);
		Configuration inputConfig =  getProject().getConfigurations().getByName(Constants.COMPILE_MODS);
		inputConfig.getResolvedConfiguration().getFiles().stream()
			.filter(file -> file.getName().endsWith(".jar"))
			.forEach(input -> {
				String outputName = input.getName().substring(0, input.getName().length() - 4) + "-mapped-" + extension.pomfVersion  + ".jar";//TODO use the hash of the input file or something?
				File output = new File(Constants.REMAPPED_MODS_STORE.get(extension), outputName);
				if(!output.getParentFile().exists()){
					output.mkdirs();
				}
				getProject().getLogger().lifecycle(":remapping jar " + input.getName());
				ModProccessor.handleMod(input, output, getProject());
				Validate.isTrue(output.exists());
				getProject().getDependencies().add(Constants.COMPILE_MODS_PROCESSED, getProject().files(output.getPath()));
			});
	}
}
