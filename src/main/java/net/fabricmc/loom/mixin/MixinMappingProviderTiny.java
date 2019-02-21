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

import net.fabricmc.tinyremapper.TinyUtils;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class MixinMappingProviderTiny extends MappingProvider {

    private final String from, to;

    public MixinMappingProviderTiny(Messager messager, Filer filer, String from, String to) {
        super(messager, filer);
        this.from = from;
        this.to = to;
    }

    private static String[] removeFirst(String[] src, int count) {
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
        MappingMethod mapped = this.methodMap.get(method);
        if (mapped != null) {
            return mapped;
        }

        try {
            Class c = this.getClass().getClassLoader().loadClass(method.getOwner().replace('/', '.'));
            if (c == null || c == Object.class) {
                return null;
            }

            for (Class cc : c.getInterfaces()) {
                mapped = getMethodMapping(method.move(cc.getName().replace('.', '/')));
                if (mapped != null) {
                    mapped = mapped.move(classMap.getOrDefault(method.getOwner(), method.getOwner()));
                    methodMap.put(method, mapped);
                    return mapped;
                }
            }

            if (c.getSuperclass() != null) {
                mapped = getMethodMapping(method.move(c.getSuperclass().getName().replace('.', '/')));
                if (mapped != null) {
                    mapped = mapped.move(classMap.getOrDefault(method.getOwner(), method.getOwner()));
                    methodMap.put(method, mapped);
                    return mapped;
                }
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public MappingField getFieldMapping(MappingField field) {
        MappingField mapped = this.fieldMap.get(field);
        if (mapped != null) {
            return mapped;
        }

        return null;

		/* try {
			Class c = this.getClass().getClassLoader().loadClass(field.getOwner().replace('/', '.'));
			if (c == null || c == Object.class) {
				return null;
			}

			if (c.getSuperclass() != null) {
				mapped = getFieldMapping(field.move(c.getSuperclass().getName().replace('.', '/')));
				if (mapped != null)
					return mapped;
			}

			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} */
    }

    // TODO: Unify with tiny-remapper

    @Override
    public void read(File input) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
            TinyUtils.read(reader, from, to, classMap::put, (fieldFrom, fieldTo) -> {
                fieldMap.put(//
                        new MappingField(fieldFrom.owner, fieldFrom.name, fieldFrom.desc),//
                        new MappingField(fieldTo.owner, fieldTo.name, fieldTo.desc)//
                );
            }, (methodFrom, methodTo) -> {
                methodMap.put(//
                        new MappingMethod(methodFrom.owner, methodFrom.name, methodFrom.desc),//
                        new MappingMethod(methodTo.owner, methodTo.name, methodTo.desc)//
                );
            });
        }
    }
}
