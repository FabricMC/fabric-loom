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

import net.fabricmc.loom.util.StaticPathWatcher;
import net.fabricmc.mappings.Mappings;
import org.gradle.api.logging.Logging;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class MappingsCache {
    public static final MappingsCache INSTANCE = new MappingsCache();

    private final Map<Path, SoftReference<Mappings>> mappingsCache = new HashMap<>();

    public Mappings get(Path mappingsPath) {
        mappingsPath = mappingsPath.toAbsolutePath();
        if (StaticPathWatcher.INSTANCE.hasFileChanged(mappingsPath)) {
            mappingsCache.remove(mappingsPath);
        }

        SoftReference<Mappings> ref = mappingsCache.get(mappingsPath);
        if (ref != null && ref.get() != null) {
            return ref.get();
        } else {
            try (InputStream stream = Files.newInputStream(mappingsPath)) {
                //TODO: replace this with V2 mappings (new seperate MappingsV2 interface I think)
                Mappings mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false);
                ref = new SoftReference<>(mappings);
                mappingsCache.put(mappingsPath, ref);
                return mappings;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
