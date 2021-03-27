package net.fabricmc.loom.util

import io.javalin.Javalin
import org.apache.commons.io.IOUtils

trait MockMavenServerTrait extends ProjectTestTrait {
    public final int mavenServerPort = 9876
    public final File testMavenDir = File.createTempDir()
    private Javalin server

    @SuppressWarnings('unused')
    def setupSpec() {
        println "Maven server path: ${testMavenDir.absolutePath}"

        server = Javalin.create { config ->
            config.enableDevLogging()
        }.start(mavenServerPort)

        /**
         * A very very basic maven server impl, DO NOT copy this and use in production as its not secure
         */
        server.get("*") { ctx ->
            println "GET: " + ctx.path()
            File file = getMavenPath(ctx.path())

            if (!file.exists()) {
                ctx.status(404)
                return
            }

            ctx.result(file.bytes)
        }

        server.put("*") { ctx ->
            println "PUT: " + ctx.path()
            File file = getMavenPath(ctx.path())
            file.parentFile.mkdirs()

            file.withOutputStream {
                IOUtils.copy(ctx.bodyAsInputStream(), it)
            }
        }
    }

    @SuppressWarnings('unused')
    def setup() {
        System.setProperty('loom.test.mavenPort', port())
    }

    @SuppressWarnings('unused')
    def cleanupSpec() {
        server.stop()
        super.cleanupSpec()
    }

    File getMavenDirectory() {
        new File(testMavenDir, "maven")
    }

    File getMavenPath(String path) {
        new File(getMavenDirectory(), path)
    }

    String port() {
        "${mavenServerPort}"
    }
}