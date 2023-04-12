package net.fabricmc.loom.test.unit.library

import net.fabricmc.loom.configuration.providers.minecraft.library.Library
import net.fabricmc.loom.configuration.providers.minecraft.library.MinecraftLibraryHelper
import net.fabricmc.loom.test.util.MinecraftTestUtils
import net.fabricmc.loom.test.util.PlatformTestUtils
import spock.lang.Specification

class MinecraftLibraryHelperTest extends Specification {
    static List<String> VERSIONS = ["1.19.4", "1.18.2", "1.16.5", "1.13.2", "1.8.9", "1.4.7", "1.2.5", "b1.8.1", "a1.2.5"]

    def "get libraries"() {
        // A quick test to make sure that nothing fails when getting the libraries for all platforms on a range of versions.
        when:
        def meta = MinecraftTestUtils.getVersionMeta(id)
        def libraries = MinecraftLibraryHelper.getLibrariesForPlatform(meta, platform)

        then:
        libraries.size() > 0

        where:
        platform << PlatformTestUtils.ALL * VERSIONS.size()
        id << VERSIONS * PlatformTestUtils.ALL.size()
    }

    def "find macos natives"() {
        when:
        def meta = MinecraftTestUtils.getVersionMeta("1.18.2")
        def libraries = MinecraftLibraryHelper.getLibrariesForPlatform(meta, PlatformTestUtils.MAC_OS_X64)

        then:
        libraries.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES } != null
        libraries.find { it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES }.classifier() == "natives-macos"
    }

    def "dont find macos natives"() {
        when:
        def meta = MinecraftTestUtils.getVersionMeta("1.18.2")
        def libraries = MinecraftLibraryHelper.getLibrariesForPlatform(meta, PlatformTestUtils.WINDOWS_X64)

        then:
        libraries.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES } == null
    }
}
