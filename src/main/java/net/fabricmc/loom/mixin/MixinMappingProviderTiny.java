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

package net.fabricmc.loom.mixin;

import cuchaz.enigma.analysis.Access;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;

public class MixinMappingProviderTiny extends MappingProvider {
    private final String from, to;

    private static class SimpleClassMapper extends Remapper {
        final Map<String, String> classMap;

        public SimpleClassMapper(Map<String, String> map) {
            this.classMap = map;
        }

        @Override
        public String map(String typeName) {
            return classMap.get(typeName);
        }
    }

    public MixinMappingProviderTiny(Messager messager, Filer filer, String from, String to) {
        super(messager, filer);
        this.from = from;
        this.to = to;
    }

    private static final String[] removeFirst(String[] src, int count) {
        if (count >= src.length) {
            return new String[0];
        } else {
            String[] out = new String[src.length - count];
            System.arraycopy(src, count, out, 0, out.length);
            return out;
        }
    }


    @Override
    public MappingMethod getMethodMapping(MappingMethod method) {
        System.out.println("processing " + method.getName() + method.getDesc());

        MappingMethod mapped = this.methodMap.get(method);
        if (mapped != null) return mapped;

        try {
            Class c = this.getClass().getClassLoader().loadClass(method.getOwner().replace('/', '.'));
            if (c == null || c == Object.class) {
                return null;
            }

            for (Class cc : c.getInterfaces()) {
                mapped = getMethodMapping(method.move(cc.getName().replace('.', '/')));
                if (mapped != null) return mapped;
            }

            if (c.getSuperclass() != null) {
                mapped = getMethodMapping(method.move(c.getSuperclass().getName().replace('.', '/')));
                if (mapped != null) return mapped;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public MappingField getFieldMapping(MappingField field) {
        System.out.println("processing " + field.getOwner() + "/" + field.getName() + field.getDesc());

        MappingField mapped = this.fieldMap.get(field);
        if (mapped != null) return mapped;

        try {
            Class c = this.getClass().getClassLoader().loadClass(field.getOwner().replace('/', '.'));
            if (c == null || c == Object.class) {
                return null;
            }

            for (Class cc : c.getInterfaces()) {
                mapped = getFieldMapping(field.move(cc.getName().replace('.', '/')));
                if (mapped != null) return mapped;
            }

            if (c.getSuperclass() != null) {
                mapped = getFieldMapping(field.move(c.getSuperclass().getName().replace('.', '/')));
                if (mapped != null) return mapped;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // TODO: Unify with tiny-remapper

    @Override
    public void read(File input) throws IOException {
        BufferedReader reader = Files.newBufferedReader(input.toPath());
        String[] header = reader.readLine().split("\t");
        if (header.length <= 1
                || !header[0].equals("v1")) {
            throw new IOException("Invalid mapping version!");
        }

        List<String> headerList = Arrays.asList(removeFirst(header, 1));
        int fromIndex = headerList.indexOf(from);
        int toIndex = headerList.indexOf(to);

        if (fromIndex < 0) throw new IOException("Could not find mapping '" + from + "'!");
        if (toIndex < 0) throw new IOException("Could not find mapping '" + to + "'!");

        Map<String, String> obfFrom = new HashMap<>();
        Map<String, String> obfTo = new HashMap<>();
        List<String[]> linesStageTwo = new ArrayList<>();

        String line;
        while ((line = reader.readLine()) != null) {
            String[] splitLine = line.split("\t");
            if (splitLine.length >= 2) {
                if ("CLASS".equals(splitLine[0])) {
                    classMap.put(splitLine[1 + fromIndex], splitLine[1 + toIndex]);
                    obfFrom.put(splitLine[1], splitLine[1 + fromIndex]);
                    obfTo.put(splitLine[1], splitLine[1 + toIndex]);
                } else {
                    linesStageTwo.add(splitLine);
                }
            }
        }

        SimpleClassMapper descObfFrom = new SimpleClassMapper(obfFrom);
        SimpleClassMapper descObfTo = new SimpleClassMapper(obfTo);

        for (String[] splitLine : linesStageTwo) {
            if ("FIELD".equals(splitLine[0])) {
                String ownerFrom = obfFrom.getOrDefault(splitLine[1], splitLine[1]);
                String ownerTo = obfTo.getOrDefault(splitLine[1], splitLine[1]);
                String descFrom = descObfFrom.mapDesc(splitLine[2]);
                String descTo = descObfTo.mapDesc(splitLine[2]);
                fieldMap.put(
                        new MappingField(ownerFrom, splitLine[3 + fromIndex], descFrom),
                        new MappingField(ownerTo, splitLine[3 + toIndex], descTo)
                );
            } else if ("METHOD".equals(splitLine[0])) {
                String ownerFrom = obfFrom.getOrDefault(splitLine[1], splitLine[1]);
                String ownerTo = obfTo.getOrDefault(splitLine[1], splitLine[1]);
                String descFrom = descObfFrom.mapMethodDesc(splitLine[2]);
                String descTo = descObfTo.mapMethodDesc(splitLine[2]);
                methodMap.put(
                        new MappingMethod(ownerFrom, splitLine[3 + fromIndex], descFrom),
                        new MappingMethod(ownerTo, splitLine[3 + toIndex], descTo)
                );
            }
        }
    }
}
