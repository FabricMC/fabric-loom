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

package net.fabricmc.loom.mixin;

import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.ObfuscationType;
import org.spongepowered.tools.obfuscation.mapping.IMappingConsumer;
import org.spongepowered.tools.obfuscation.mapping.common.MappingWriter;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by asie on 10/9/16.
 */
public class MixinMappingWriterTiny extends MappingWriter {

    public MixinMappingWriterTiny(Messager messager, Filer filer) {
        super(messager, filer);
    }

    @Override
    public void write(String output, ObfuscationType type, IMappingConsumer.MappingSet<MappingField> fields, IMappingConsumer.MappingSet<MappingMethod> methods) {
        if (output != null) {
            String from = type.getKey().split(":")[0];
            String to = type.getKey().split(":")[1];

            try (PrintWriter writer = this.openFileWriter(output, type + " output TinyMappings")) {
                writer.println(String.format("v1\t%s\t%s", from, to));
                for (IMappingConsumer.MappingSet.Pair<MappingField> pair : fields) {
                    writer.println(String.format("FIELD\t%s\t%s\t%s\t%s", pair.from.getOwner(), pair.from.getDesc(), pair.from.getSimpleName(), pair.to.getSimpleName()));
                }
                for (IMappingConsumer.MappingSet.Pair<MappingMethod> pair : methods) {
                    writer.println(String.format("METHOD\t%s\t%s\t%s\t%s", pair.from.getOwner(), pair.from.getDesc(), pair.from.getSimpleName(), pair.to.getSimpleName()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
