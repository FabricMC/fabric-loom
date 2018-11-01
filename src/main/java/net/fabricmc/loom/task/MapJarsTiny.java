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


import com.google.common.base.Joiner;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MapJarsTiny {

	public void mapJars(MapJarsTask task) throws IOException {
		String fromM = "mojang";
		String toM = "pomf";

		Path mappings = task.getMappingFile().toPath();
		Path[] classpath = task.getMapperPaths().stream()
				.map(File::toPath)
				.toArray(Path[]::new);

		task.getLogger().lifecycle(":remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
				.build();

		Path input = task.getInputJar().toPath();
		Path output = task.getMappedJar().toPath();

		try {
			OutputConsumerPath outputConsumer = new OutputConsumerPath(output);
			outputConsumer.addNonClassFiles(input);
			remapper.read(input);
			remapper.read(classpath);
			remapper.apply(input, outputConsumer);
			outputConsumer.finish();
			remapper.finish();
		} catch (Exception e){
			remapper.finish();
			throw new RuntimeException("Failed to remap minecraft to " + toM, e);
		}
	}
}
