/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.ZipUtils;

public class AccessWidenerUtils {
	/**
	 * Remap a mods access widener from intermediary to named, so that loader can apply it in our dev-env.
	 */
	public static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		int version = AccessWidenerReader.readVersion(input);

		AccessWidenerWriter writer = new AccessWidenerWriter(version);
		AccessWidenerRemapper awRemapper = new AccessWidenerRemapper(
				writer,
				remapper,
				MappingsNamespace.INTERMEDIARY.toString(),
				MappingsNamespace.NAMED.toString()
		);
		AccessWidenerReader reader = new AccessWidenerReader(awRemapper);
		reader.read(input);
		return writer.write();
	}

	public static AccessWidenerData readAccessWidenerData(Path inputJar) throws IOException {
		byte[] modJsonBytes = ZipUtils.unpack(inputJar, "fabric.mod.json");
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("accessWidener")) {
			return null;
		}

		String accessWidenerPath = jsonObject.get("accessWidener").getAsString();
		byte[] accessWidener = ZipUtils.unpack(inputJar, accessWidenerPath);
		AccessWidenerReader.Header header = AccessWidenerReader.readHeader(accessWidener);

		return new AccessWidenerData(accessWidenerPath, header, accessWidener);
	}

	public record AccessWidenerData(String path, AccessWidenerReader.Header header, byte[] content) {
	}
}
