package net.fabricmc.loom.test.unit.library.processors

import net.fabricmc.loom.configuration.providers.minecraft.library.Library
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager
import net.fabricmc.loom.configuration.providers.minecraft.library.MinecraftLibraryHelper

import net.fabricmc.loom.test.util.MinecraftTestUtils
import net.fabricmc.loom.util.Platform
import org.gradle.api.JavaVersion
import spock.lang.Specification

abstract class LibraryProcessorTest extends Specification {
    def getLibs(String id, Platform platform) {
        def meta = MinecraftTestUtils.getVersionMeta(id)
        def libraries = MinecraftLibraryHelper.getLibrariesForPlatform(meta, platform)
        def context = new LibraryContext(meta, JavaVersion.VERSION_17)
        def manager = new LibraryProcessorManager(platform)
        return [libraries, context, manager]
    }

    boolean hasLibrary(String name, List<Library> libraries) {
        libraries.any { it.is(name)}
    }
}