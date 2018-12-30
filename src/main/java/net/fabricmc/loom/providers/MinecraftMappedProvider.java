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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.MapJarsTiny;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;

public class MinecraftMappedProvider extends DependencyProvider {
    public File MINECRAFT_MAPPED_JAR;
    public File MINECRAFT_INTERMEDIARY_JAR;

    private MinecraftProvider minecraftProvider;

    @Override
    public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
        if (!extension.getMappingsProvider().MAPPINGS_TINY.exists()) {
            throw new RuntimeException("mappings file not found");
        }

        if (!extension.getMinecraftProvider().jarProvider.getMergedJar().exists()) {
            throw new RuntimeException("input merged jar not found");
        }

        if(!getMappedJar().exists() || !getIntermediaryJar().exists()){
            if (getMappedJar().exists()) {
                getMappedJar().delete();
            }
            if (getIntermediaryJar().exists()) {
                getIntermediaryJar().delete();
            }
            new MapJarsTiny().mapJars(minecraftProvider.jarProvider, this, project);
        }

        if (!MINECRAFT_MAPPED_JAR.exists()) {
            throw new RuntimeException("mapped jar not found");
        }

        String version = minecraftProvider.minecraftVersion + "-mapped-" + extension.getMappingsProvider().mappingsVersion;
        project.getDependencies().add(Constants.MINECRAFT_NAMED, project.getDependencies().module("net.minecraft:minecraft:" + version));
        version = minecraftProvider.minecraftVersion + "-intermediary";
        project.getDependencies().add(Constants.MINECRAFT_INTERMEDIARY, project.getDependencies().module("net.minecraft:minecraft:" + version));
    }

    public void initFiles(Project project, MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        this.minecraftProvider = minecraftProvider;
        MINECRAFT_INTERMEDIARY_JAR = new File(extension.getUserCache(), "minecraft-" + minecraftProvider.minecraftVersion + "-intermediary.jar");
        MINECRAFT_MAPPED_JAR = new File(extension.getUserCache(), "minecraft-" + minecraftProvider.minecraftVersion + "-mapped-" + mappingsProvider.mappingsVersion + ".jar");
    }

    public Collection<File> getMapperPaths() {
        return minecraftProvider.libraryProvider.getLibraries();
    }

    public File getIntermediaryJar() {
        return MINECRAFT_INTERMEDIARY_JAR;
    }

    public File getMappedJar() {
        return MINECRAFT_MAPPED_JAR;
    }

    @Override
    public String getTargetConfig() {
        return Constants.MINECRAFT_NAMED;
    }
}
