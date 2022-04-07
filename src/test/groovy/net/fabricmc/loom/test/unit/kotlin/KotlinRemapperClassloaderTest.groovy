package net.fabricmc.loom.test.unit.kotlin


import net.fabricmc.loom.util.kotlin.KotlinClasspath
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.api.TrRemapper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

class KotlinRemapperClassloaderTest extends Specification {
    private static String KOTLIN_VERSION = "1.6.10"
    private static String KOTLIN_URL = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/${KOTLIN_VERSION}/kotlin-stdlib-${KOTLIN_VERSION}.jar"

    def "Test Koltin Remapper Classloader"() {
        given:
            def classLoader = KotlinRemapperClassloader.create(new TestKotlinClasspath())
            def mockTrClass = Mock(TrClass)
            def mockEnv = Mock(TrEnvironment)
            def mockRemapper = Mock(TrRemapper)

            mockEnv.remapper >> mockRemapper
            mockTrClass.environment >> mockEnv

            def classReader = new ClassReader(getClassBytes("TestExtensionKt"))

        when:
            def extension = classLoader.tinyRemapperExtension
            def visitor = extension.insertApplyVisitor(mockTrClass, new ClassNode())

            classReader.accept(visitor, 0)

        then:
            extension != null
            visitor != null

            // Ensure that the visitor is using the kotlin version specified on the classpath.
            visitor.runtimeKotlinVersion == KOTLIN_VERSION
    }

    private class TestKotlinClasspath implements KotlinClasspath {
        @Override
        String version() {
            return KOTLIN_VERSION
        }

        @Override
        Set<URL> classpath() {
            def file = downloadFile(KOTLIN_URL, "kotlin-stdlib.jar")

            return Set.of(
                    file.toURI().toURL()
            )
        }
    }

    File tempDir = File.createTempDir()
    File downloadFile(String url, String name) {
        File dst = new File(tempDir, name)
        dst.parentFile.mkdirs()
        dst << new URL(url).newInputStream()
        return dst
    }

    def getClassBytes(name) {
        return new File("src/test/resources/classes/${name}.class").bytes
    }
}
