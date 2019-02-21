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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.tools.obfuscation.service.IObfuscationService;
import org.spongepowered.tools.obfuscation.service.ObfuscationTypeDescriptor;

import java.util.Collection;
import java.util.Set;

public class ObfuscationServiceFabric implements IObfuscationService {

    public static final String IN_MAP_FILE = "inMapFile";
    public static final String IN_MAP_EXTRA_FILES = "inMapExtraFiles";
    public static final String OUT_MAP_FILE = "outMapFile";

    private String asSuffixed(String arg, String from, String to) {
        return arg + StringUtils.capitalize(from) + StringUtils.capitalize(to);
    }

    private ObfuscationTypeDescriptor createObfuscationType(String from, String to) {
        return new ObfuscationTypeDescriptor(//
                from + ":" + to,//
                asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to),//
                asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to),//
                asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to),//
                ObfuscationEnvironmentFabric.class//
        );
    }

    private void addSupportedOptions(ImmutableSet.Builder<String> builder, String from, String to) {
        builder.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to));
        builder.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to));
        builder.add(asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to));
    }

    @Override
    public Set<String> getSupportedOptions() {
        ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
        addSupportedOptions(builder, "official", "intermediary");
        addSupportedOptions(builder, "official", "named");
        addSupportedOptions(builder, "intermediary", "official");
        addSupportedOptions(builder, "intermediary", "named");
        addSupportedOptions(builder, "named", "official");
        addSupportedOptions(builder, "named", "intermediary");
        return builder.build();
    }

    @Override
    public Collection<ObfuscationTypeDescriptor> getObfuscationTypes() {
        return ImmutableSet.of(//
                createObfuscationType("official", "intermediary"),//
                createObfuscationType("official", "named"),//
                createObfuscationType("intermediary", "official"),//
                createObfuscationType("intermediary", "named"),//
                createObfuscationType("named", "official"),//
                createObfuscationType("named", "intermediary")//
        );
    }
}
