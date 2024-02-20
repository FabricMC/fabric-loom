package net.fabricmc.loom.test.unit.cache

import net.fabricmc.loom.decompilers.ClassLineNumbers
import net.fabricmc.loom.decompilers.cache.CachedData
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class CachedDataTest extends Specification {
    @TempDir
    Path testPath

    // Simple test to check if the CachedData class can be written and read from a file
    def "Read + Write CachedData"() {
        given:
        def lineNumberEntry = new ClassLineNumbers.Entry("Test", 1, 2, [1: 2, 4: 7])
        def cachedData = new CachedData("Example sources", lineNumberEntry)
        def path = testPath.resolve("cachedData.bin")
        when:
        // Write the cachedData to a file
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable {
            cachedData.write(it)
        }

        // And read it back
        def readCachedData = Files.newInputStream(path).withCloseable {
            return CachedData.read(it)
        }

        then:
        cachedData == readCachedData
    }
}
