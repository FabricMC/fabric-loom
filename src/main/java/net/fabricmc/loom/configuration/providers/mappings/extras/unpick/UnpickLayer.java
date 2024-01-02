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

package net.fabricmc.loom.configuration.providers.mappings.extras.unpick;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;

@ApiStatus.Experimental
public interface UnpickLayer {
	@Nullable
	UnpickData getUnpickData() throws IOException;

	record UnpickData(Metadata metadata, byte[] definitions) {
		public static UnpickData read(Path metadataPath, Path definitionPath) throws IOException {
			final byte[] definitions = Files.readAllBytes(definitionPath);
			final Metadata metadata;

			try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
				metadata = LoomGradlePlugin.GSON.fromJson(reader, Metadata.class);
			}

			return new UnpickData(metadata, definitions);
		}

		public record Metadata(int version, String unpickGroup, String unpickVersion) {
			public String asJson() {
				return LoomGradlePlugin.GSON.toJson(this);
			}
		}
	}
}
