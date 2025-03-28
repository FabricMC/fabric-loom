/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.mods.dependency.refmap;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.fmj.mixin.MixinRefmap;

public record MixinReferenceRemapperImpl(Map<String, MixinRefmap.ReferenceMappingData> data) implements MixinReferenceRemapper {
	private static final Logger LOGGER = LoggerFactory.getLogger(MixinReferenceRemapperImpl.class);

	public static MixinReferenceRemapper createFromRefmaps(String from, String to, Stream<MixinRefmap> refmaps) {
		MixinRefmap.NamespacePair namespaces = new MixinRefmap.NamespacePair(from, to);

		Map<String, MixinRefmap.ReferenceMappingData> data = refmaps
				.map(refmap -> refmap.getData(namespaces))
				.filter(Objects::nonNull)
				.map(MixinRefmap.MixinMappingData::data)
				.flatMap(map -> map.entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(existing, replacement) -> {
							// TODO we could merge this, but it should never happen in practice
							LOGGER.warn("Duplicate mixin reference mapping for {} in refmaps, using the first one", existing);
							return existing;
						}
				));

		return new MixinReferenceRemapperImpl(data);
	}

	@Override
	public String remapReference(String mixinClassName, String reference) {
		final MixinRefmap.ReferenceMappingData data = data().get(mixinClassName);

		if (data != null) {
			return data.remap(reference);
		}

		return reference;
	}
}
