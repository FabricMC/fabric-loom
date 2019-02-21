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

package net.fabricmc.loom.tasks.sourceremap;

import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.Utils;
import net.fabricmc.stitch.util.StitchUtil;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;

import java.io.File;
import java.io.FileReader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by covers1624 on 12/02/19.
 */
public class ForkedMercuryExecutor {

    public static void main(String[] args) throws Exception {
        List<File> libraries = new ArrayList<>();
        File input = null;
        File output = null;
        List<File> mappings = new ArrayList<>();
        String from = null;
        String to = null;
        for (String line : args) {
            //-C= Similar to FF's cmd args.
            String opt = line.substring(0, 3);
            String data = line.substring(3);
            switch (opt.charAt(1)) {
                case 'l':
                    libraries.add(new File(data));
                    break;
                case 'i':
                    if (input != null) {
                        throw new RuntimeException("Input already set.");
                    }
                    input = new File(data);
                    break;
                case 'o':
                    if (output != null) {
                        throw new RuntimeException("Output already set.");
                    }
                    output = new File(data);
                    break;
                case 'm':
                    mappings.add(new File(data));
                    break;
                case 'f':
                    if (from != null) {
                        throw new RuntimeException("From mappings already set.");
                    }
                    from = data;
                    break;
                case 't':
                    if (to != null) {
                        throw new RuntimeException("To mappings already set.");
                    }
                    to = data;
                    break;
                default:
                    throw new RuntimeException("Switch falloff: " + opt);
            }
        }
        Objects.requireNonNull(input, "Input not set.");
        Objects.requireNonNull(output, "Output not set.");
        Objects.requireNonNull(from, "From mappings not set.");
        Objects.requireNonNull(to, "To mappings not set.");
        runMercury(libraries, input, output, mappings, from, to);
    }

    public static void runMercury(List<File> libraries, File input, File output, List<File> mappings, String from, String to) throws Exception {
        Mercury mercury = new Mercury();
        libraries.forEach(f -> mercury.getClassPath().add(f.toPath()));

        Path inputPath = input.toPath();
        boolean tempInput = false;
        if (!input.isDirectory()) {
            tempInput = true;
            inputPath = Files.createTempDirectory("loom-extract");
            Utils.extract(input, inputPath);
        }

        //TODO Remap in place stuff.
        Path outputPath = output.toPath();
        FileSystem outputFs = null;
        if (!output.isDirectory()) {
            if (output.exists()) {
                output.delete();
            }
            outputFs = StitchUtil.getJarFileSystem(output, true).get();
            outputPath = outputFs.getPath("/");
        }

        MappingSet mappingSet = MappingSet.create();
        for (File mappingsFile : mappings) {
            try (TinyReader reader = new TinyReader(new FileReader(mappingsFile), from, to)) {
                reader.read(mappingSet);
            }
        }
        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

        try {
            mercury.rewrite(inputPath, outputPath);
        } finally {
            if (tempInput) {
                Files.walkFileTree(inputPath, new DeletingFileVisitor());
            }
            if (outputFs != null) {
                outputFs.close();
            }
        }
    }
}
