/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.test.unit.layeredmappings

import net.fabricmc.loom.api.mappings.layered.MappingContext
import net.fabricmc.loom.api.mappings.layered.MappingLayer
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec
import net.fabricmc.loom.configuration.providers.mappings.IntermediateMappingsService
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsProcessor
import net.fabricmc.loom.configuration.providers.mappings.extras.unpick.UnpickLayer
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider
import net.fabricmc.loom.test.unit.LoomMocks
import net.fabricmc.mappingio.adapter.MappingDstNsReorder
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import spock.lang.Specification

import java.nio.file.Path
import java.util.function.Supplier
import java.util.zip.ZipFile

abstract class LayeredMappingsSpecification extends Specification implements LayeredMappingsTestConstants {
    Logger mockLogger = Mock(Logger)
    MinecraftProvider mockMinecraftProvider = Mock(MinecraftProvider)
    String intermediaryUrl
    MappingContext mappingContext = new TestMappingContext()

    File tempDir = File.createTempDir()

    Map<String, File> mavenFiles = [:]

    def withMavenFile(String mavenNotation, File file) {
        mavenFiles.put(mavenNotation, file)
    }

    File downloadFile(String url, String name) {
        File dst = new File(tempDir, name)
        dst.parentFile.mkdirs()
        dst << new URL(url).newInputStream()
        return dst
    }

    File extractFileFromZip(File zipFile, String name) {
        File dst = new File(tempDir, name)
        dst.parentFile.mkdirs()

        new ZipFile(zipFile).withCloseable {
            dst << it.getInputStream(it.getEntry(name))
        }
        return dst
    }

    MemoryMappingTree getSingleMapping(MappingsSpec<? extends MappingLayer> spec) {
        MemoryMappingTree mappingTree = new MemoryMappingTree()
        spec.createLayer(mappingContext).visit(mappingTree)
        return mappingTree
    }

    MemoryMappingTree getLayeredMappings(MappingsSpec<? extends MappingLayer>... specs) {
        LayeredMappingSpec spec = new LayeredMappingSpec(specs.toList())
        LayeredMappingsProcessor processor = new LayeredMappingsProcessor(spec)
        return processor.getMappings(processor.resolveLayers(mappingContext))
    }

    UnpickLayer.UnpickData getUnpickData(MappingsSpec<? extends MappingLayer>... specs) {
        LayeredMappingSpec spec = new LayeredMappingSpec(specs.toList())
        LayeredMappingsProcessor processor = new LayeredMappingsProcessor(spec)
        return processor.getUnpickData(processor.resolveLayers(mappingContext))
    }

    String getTiny(MemoryMappingTree mappingTree) {
        def sw = new StringWriter()
        mappingTree.accept(new Tiny2Writer(sw, false))
        return sw.toString()
    }

    MemoryMappingTree reorder(MemoryMappingTree mappingTree) {
        def reorderedMappings = new MemoryMappingTree()
        def nsReorder = new MappingDstNsReorder(reorderedMappings, Collections.singletonList(MappingsNamespace.NAMED.toString()))
        def nsSwitch = new MappingSourceNsSwitch(nsReorder, MappingsNamespace.INTERMEDIARY.toString(), true)
        mappingTree.accept(nsSwitch)
        return reorderedMappings
    }

    def setup() {
        mockMinecraftProvider.file(_) >> { args ->
            return new File(tempDir, args[0])
        }
    }

    class TestMappingContext implements MappingContext {
        @Override
        Path resolveDependency(Dependency dependency) {
            throw new UnsupportedOperationException("TODO")
        }

        @Override
        Path resolveMavenDependency(String mavenNotation) {
            assert mavenFiles.containsKey(mavenNotation)
            return mavenFiles.get(mavenNotation).toPath()
        }

        @Override
        Supplier<MemoryMappingTree> intermediaryTree() {
            return {
                IntermediateMappingsService.create(LoomMocks.intermediaryMappingsProviderMock("test", intermediaryUrl), minecraftProvider()).memoryMappingTree
            }
        }

        @Override
        MinecraftProvider minecraftProvider() {
            return mockMinecraftProvider
        }

        @Override
        Path workingDirectory(String name) {
            return new File(tempDir, name).toPath()
        }

        @Override
        Logger getLogger() {
            return mockLogger
        }
    }
}
