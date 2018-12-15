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

package net.fabricmc.loom.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

public final class MixinRefmapHelper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MixinRefmapHelper() {

    }
    public static boolean addRefmapName(String filename, String mixinVersion, File output) {
        Set<String> mixinFilenames = findMixins(output, true);

        if (mixinFilenames.size() > 0) {
            return ZipUtil.transformEntries(
                    output,
                    mixinFilenames.stream()
                            .map((f) -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
                                @Override
                                protected String transform(ZipEntry zipEntry, String input) throws IOException {
                                    JsonObject json = GSON.fromJson(input, JsonObject.class);
                                    if (!json.has("refmap")) {
                                        json.addProperty("refmap", filename);
                                    }
                                    if (!json.has("minVersion") && mixinVersion != null) {
                                        json.addProperty("minVersion", mixinVersion);
                                    }
                                    return GSON.toJson(json);
                                }
                            })).toArray(ZipEntryTransformerEntry[]::new)
            );
        } else {
            return false;
        }
    }

    private static Set<String> findMixins(File output, boolean onlyWithoutRefmap) {
        // first, identify all of the mixin files
        Set<String> mixinFilename = new HashSet<>();
        // TODO: this is a lovely hack
        ZipUtil.iterate(output, (stream, entry) -> {
            if (!entry.isDirectory() && entry.getName().endsWith(".json") && !entry.getName().contains("/") && !entry.getName().contains("\\")) {
                // JSON file in root directory
                InputStreamReader inputStreamReader = new InputStreamReader(stream);
                try {
                    JsonObject json = GSON.fromJson(inputStreamReader, JsonObject.class);
                    if (json != null && json.has("mixins") && json.get("mixins").isJsonArray()) {
                        if (!onlyWithoutRefmap || !json.has("refmap") || !json.has("minVersion")) {
                            mixinFilename.add(entry.getName());
                        }
                    }
                } catch (Exception e) {
                    // ...
                } finally {
                    inputStreamReader.close();
                    stream.close();
                }
            }
        });
        return mixinFilename;
    }

    private static Set<String> findRefmaps(File output) {
        // first, identify all of the mixin refmaps
        Set<String> mixinRefmapFilenames = new HashSet<>();
        // TODO: this is also a lovely hack
        ZipUtil.iterate(output, (stream, entry) -> {
            if (!entry.isDirectory() && entry.getName().endsWith(".json") && !entry.getName().contains("/") && !entry.getName().contains("\\")) {
                // JSON file in root directory
                InputStreamReader inputStreamReader = new InputStreamReader(stream);
                try {
                    JsonObject json = GSON.fromJson(inputStreamReader, JsonObject.class);
                    if (json != null && json.has("refmap")) {
                        mixinRefmapFilenames.add(json.get("refmap").getAsString());
                    }
                } catch (Exception e) {
                    // ...
                } finally {
                    inputStreamReader.close();
                    stream.close();
                }
            }
        });
        return mixinRefmapFilenames;
    }

    public static boolean transformRefmaps(TinyRemapper remapper, File output) {
        Set<String> mixinRefmapFilenames = findRefmaps(output);

        if (mixinRefmapFilenames.size() > 0) {
            Remapper asmRemapper;
            // TODO: Expose in tiny-remapper
            try {
                Field f = TinyRemapper.class.getDeclaredField("remapper");;
                f.setAccessible(true);
                asmRemapper = (Remapper) f.get(remapper);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ZipUtil.transformEntries(
                    output,
                    mixinRefmapFilenames.stream()
                            .map((f) -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
                                @Override
                                protected String transform(ZipEntry zipEntry, String input) throws IOException {
                                    return transformRefmap(asmRemapper, GSON, input);
                                }
                            })).toArray(ZipEntryTransformerEntry[]::new)
            );

            return true;
        } else {
            return false;
        }
    }

    public static String transformRefmap(Remapper remapper, Gson gson, String input) throws IOException {
        try {
            JsonObject refMap = gson.fromJson(input, JsonObject.class);
            JsonObject mappings = refMap.getAsJsonObject("mappings");

            for (Map.Entry<String, JsonElement> elementEntry : mappings.entrySet()) {
                JsonObject value = elementEntry.getValue().getAsJsonObject();
                for (String k : new HashSet<>(value.keySet())) {
                    try {
                        String v = value.get(k).getAsString();
                        String v2;

                        if (v.charAt(0) == 'L') {
                            // field or member
                            MemberInfo info = MemberInfo.parse(v);
                            String owner = remapper.map(info.owner);
                            if (info.isField()) {
                                v2 = new MemberInfo(
                                        remapper.mapFieldName(info.owner, info.name, info.desc),
                                        owner,
                                        remapper.mapDesc(info.desc)
                                ).toString();
                            } else {
                                v2 = new MemberInfo(
                                        remapper.mapMethodName(info.owner, info.name, info.desc),
                                        owner,
                                        remapper.mapMethodDesc(info.desc)
                                ).toString();
                            }
                        } else {
                            // class
                            v2 = remapper.map(v);
                        }

                        if (v2 != null) {
                            value.addProperty(k, v2);
                        }
                    } catch (Exception ee) {
                        ee.printStackTrace();
                    }
                }
            }

            return gson.toJson(refMap);
        } catch (Exception e) {
            e.printStackTrace();
            return input;
        }
    }
}
