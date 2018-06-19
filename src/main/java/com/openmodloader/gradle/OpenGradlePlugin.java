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

package com.openmodloader.gradle;

import net.fabricmc.loom.AbstractPlugin;
import net.fabricmc.loom.task.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;

public class OpenGradlePlugin extends AbstractPlugin {
	@Override
	public void apply(Project target) {
		super.apply(target);

		makeTask("download", DownloadTask.class);
		makeTask("mergeJars", MergeJarsTask.class).dependsOn("download");
		makeTask("processMods", ProcessModsTask.class).dependsOn("mergeJars");
		makeTask("mapJars", MapJarsTask.class).dependsOn("processMods");
		makeTask("finaliseJars", FinaliseJar.class).dependsOn("mapJars");
		makeTask("setup", DefaultTask.class).dependsOn("finaliseJars").setGroup("openmodloader");

		makeTask("extractNatives", ExtractNativesTask.class).dependsOn("download");
		makeTask("genIdeaWorkspace", GenIdeaProjectTask.class).dependsOn("idea").setGroup("ide");

		makeTask("vscode", GenVSCodeProjectTask.class).dependsOn("extractNatives").setGroup("ide");

		makeTask("runClient", RunClientTask.class).dependsOn("buildNeeded").setGroup("minecraft");
		makeTask("runServer", RunServerTask.class).dependsOn("buildNeeded").setGroup("minecraft");
	}
}
