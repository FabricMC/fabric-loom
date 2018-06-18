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

package net.fabricmc.loom.util.proccessing;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.task.ProcessModsTask;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PreBakeMixins {

	public void proccess(Project project, LoomGradleExtension extension, List<File> mods) throws IOException {
		project.getLogger().lifecycle(":Found " + mods.size() + " mods to prebake");
		String[] args = new String[mods.size() + 4];
		args[0] = "-m";
		args[1] = Constants.MAPPINGS_TINY.get(extension).getAbsolutePath();
		args[2] = Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath();
		args[3] = Constants.MINECRAFT_FINAL_JAR.get(extension).getAbsolutePath();
		for (int i = 0; i < mods.size(); i++) {
			args[i + 4] = mods.get(i).getAbsolutePath();
		}
		project.getLogger().lifecycle(":preBaking mixins");
		ProcessModsTask.addFile(Constants.MINECRAFT_MAPPED_JAR.get(extension), this);
		MixinPrebaker.main(args);
	}

}
