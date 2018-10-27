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

package net.fabricmc.loom.util.proccessing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.fabricmc.tinyremapper.TinyUtils;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MixinDevRemapper implements IRemapper {
	private final BiMap<String, String> classMap = HashBiMap.create();
	private final Map<TinyUtils.Mapping, TinyUtils.Mapping> fieldMap = new HashMap<>();
	private final Map<TinyUtils.Mapping, TinyUtils.Mapping> methodMap = new HashMap<>();

	private final SimpleClassMapper classMapper = new SimpleClassMapper(classMap);
	private final SimpleClassMapper classUnmapper = new SimpleClassMapper(classMap.inverse());

	private static class SimpleClassMapper extends Remapper {
		final Map<String, String> classMap;

		public SimpleClassMapper(Map<String, String> map) {
			this.classMap = map;
		}

		public String map(String typeName) {
			return this.classMap.getOrDefault(typeName, typeName);
		}
	}

	public void readMapping(BufferedReader reader, String fromM, String toM) throws IOException {
		TinyUtils.read(reader, fromM, toM, classMap::put, fieldMap::put, methodMap::put);
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		TinyUtils.Mapping mapping = methodMap.get(new TinyUtils.Mapping(owner, name, desc));
		return mapping != null ? mapping.name : name;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		TinyUtils.Mapping mapping = fieldMap.get(new TinyUtils.Mapping(owner, name, desc));
		if(mapping == null){
			//We try again using obfed names
			owner = unmap(owner);
			desc = unmapDesc(desc);
			mapping = fieldMap.get(new TinyUtils.Mapping(owner, name, desc));
		}
		return mapping != null ? mapping.name : name;
	}

	@Override
	public String map(String typeName) {
		return classMap.getOrDefault(typeName, typeName);
	}

	@Override
	public String unmap(String typeName) {
		return classMap.inverse().getOrDefault(typeName, typeName);
	}

	@Override
	public String mapDesc(String desc) {
		return classMapper.mapDesc(desc);
	}

	@Override
	public String unmapDesc(String desc) {
		return classUnmapper.mapDesc(desc);
	}
}