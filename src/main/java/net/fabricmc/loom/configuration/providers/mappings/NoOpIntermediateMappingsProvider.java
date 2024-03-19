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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;

/**
 * A bit of a hack, creates an empty intermediary mapping file to be used for mc versions without any intermediate mappings.
 */
public abstract class NoOpIntermediateMappingsProvider extends IntermediateMappingsProvider {
	private static final String HEADER_OFFICIAL_MERGED = "tiny\t2\t0\tofficial\tintermediary";
	private static final String HEADER_OFFICIAL_LEGACY_MERGED = "tiny\t2\t0\tintermediary\tclientOfficial\tserverOfficial\t";

	@Override
	public void provide(Path tinyMappings) throws IOException {
		Files.writeString(tinyMappings, getIsLegacyMinecraft().get() ? HEADER_OFFICIAL_LEGACY_MERGED : HEADER_OFFICIAL_MERGED, StandardCharsets.UTF_8);
	}

	@Override
	public @NotNull String getName() {
		return "empty-intermediate";
	}
}
