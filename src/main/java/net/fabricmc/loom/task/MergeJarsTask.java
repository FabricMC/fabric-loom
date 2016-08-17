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

import net.fabricmc.blendingjar.JarMerger;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MergeJarsTask extends DefaultTask {

	@TaskAction
	public void mergeJars() throws IOException {
		this.getLogger().lifecycle(":merging jars");
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);

		FileInputStream client = new FileInputStream(Constants.MINECRAFT_CLIENT_JAR.get(extension));
		FileInputStream server = new FileInputStream(Constants.MINECRAFT_SERVER_JAR.get(extension));
		FileOutputStream merged = new FileOutputStream(Constants.MINECRAFT_MERGED_JAR.get(extension));

		JarMerger jarMerger = new JarMerger(client, server, merged);

		jarMerger.merge();
		jarMerger.close();

		client.close();
		server.close();
		merged.close();

	}

}
