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

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

//TODO fix local mappings
//TODO possibly use maven for mappings, can fix above at the same time
public class PomfProvider {

	public String minecraftVersion;
	public String pomfVersion;

	public File POMF_DIR;
	public File MAPPINGS_TINY_GZ;
	public File MAPPINGS_TINY;
	public File MAPPINGS_MIXIN_EXPORT;

	public PomfProvider(String pomfVersion, String minecraftVersion, Project project) {
		this.pomfVersion = pomfVersion;
		this.minecraftVersion = minecraftVersion;
		initFiles(project);
		try {
			init(project);
		} catch (Exception e) {
			throw new RuntimeException("Failed to setup pomf", e);
		}
	}

	public void init(Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		project.getLogger().lifecycle(":setting up pomf " + pomfVersion);

		if (!POMF_DIR.exists()) {
			POMF_DIR.mkdir();
		}

		if (!MAPPINGS_TINY_GZ.exists()) {
			FileUtils.copyURLToFile(
				new URL(String.format("%1$s%2$s.%3$s/pomf-%2$s.%3$s-tiny.gz", Constants.POMF_MAVEN_SERVER, minecraftVersion, pomfVersion)),
				MAPPINGS_TINY_GZ);
		}

		if (!MAPPINGS_TINY.exists()) {
			GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(MAPPINGS_TINY_GZ));
			FileOutputStream fileOutputStream = new FileOutputStream(MAPPINGS_TINY);
			int length;
			byte[] buffer = new byte[4096];
			while ((length = gzipInputStream.read(buffer)) > 0) {
				fileOutputStream.write(buffer, 0, length);
			}
			gzipInputStream.close();
			fileOutputStream.close();
		}
	}

	public void initFiles(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		POMF_DIR = new File(extension.getUserCache(), "pomf");

		MAPPINGS_TINY_GZ = new File(POMF_DIR, "pomf-tiny-" + minecraftVersion + "." + pomfVersion + ".gz");
		MAPPINGS_TINY = new File(POMF_DIR, "pomf-tiny-" + minecraftVersion + "." + pomfVersion);
		MAPPINGS_MIXIN_EXPORT = new File(Constants.CACHE_FILES, "mixin-map-" + minecraftVersion + "." + pomfVersion + ".tiny");
	}

}
