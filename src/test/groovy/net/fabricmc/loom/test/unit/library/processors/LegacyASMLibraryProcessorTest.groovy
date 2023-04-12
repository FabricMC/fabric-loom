package net.fabricmc.loom.test.unit.library.processors

import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor
import net.fabricmc.loom.configuration.providers.minecraft.library.processors.LegacyASMLibraryProcessor
import net.fabricmc.loom.test.util.PlatformTestUtils

class LegacyASMLibraryProcessorTest extends LibraryProcessorTest {
    def "Removes legacy asm-all"() {
        when:
        def (original, context, manager) = getLibs("1.4.7", PlatformTestUtils.MAC_OS_X64)
        def processor = new LegacyASMLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
        def processed = manager.processLibraries(original, context)

        then:
        processor.applicationResult == LibraryProcessor.ApplicationResult.MUST_APPLY

        hasLibrary("org.ow2.asm:asm-all", original)
        !hasLibrary("org.ow2.asm:asm-all", processed)
    }
}
