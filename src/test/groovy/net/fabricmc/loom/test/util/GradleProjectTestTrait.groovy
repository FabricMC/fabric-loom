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

package net.fabricmc.loom.test.util

import groovy.transform.Immutable
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.util.ZipUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Shared

trait GradleProjectTestTrait {
    @Lazy
    @Shared
    private static File sharedProjectDir = File.createTempDir()
    @Lazy
    @Shared
    private static File sharedGradleHomeDir = File.createTempDir()

    GradleProject gradleProject(Map options) {
        String gradleVersion = options.version as String ?: LoomTestConstants.DEFAULT_GRADLE
        String warningMode = options.warningMode as String ?: "fail"
        File projectDir = options.projectDir as File ?: options.sharedFiles ? sharedProjectDir : File.createTempDir()
        File gradleHomeDir = options.gradleHomeDir as File ?: options.sharedFiles ? sharedGradleHomeDir : File.createTempDir()

        setupProject(options, projectDir)

        return new GradleProject(
                gradleVersion: gradleVersion,
                projectDir: projectDir.absolutePath,
                gradleHomeDir: gradleHomeDir.absolutePath,
                warningMode: warningMode
        )
    }

    private void setupProject(Map options, File projectDir) {
        if (options.project) {
            copyProjectFromResources(options.project as String, projectDir)
            return
        }

        if (options.repo) {
            String repo  = options.repo
            String commit = options.commit

            exec(projectDir, "git", "clone", repo, ".")
            exec(projectDir, "git", "checkout", commit)

            if (options.patch) {
                def patchFile = new File("src/test/resources/patches/${options.patch}.patch")

                if (!patchFile.exists()) {
                    throw new FileNotFoundException("Could not find patch file at: " + patchFile.absolutePath)
                }

                exec(projectDir, "git", "apply", patchFile.absolutePath)
            }

            return
        }

        throw new UnsupportedOperationException("No project setup method was supplied")
    }

    private void exec(File projectDir, String... args) {
        def process = args.execute([], projectDir)
        process.consumeProcessOutput(System.out, System.err)

        def exitCode = process.waitFor()

        if (exitCode != 0) {
             throw new RuntimeException("Command failed with exit code: $exitCode")
        }
    }

    private void copyProjectFromResources(String project, File projectDir) {
        def projectSourceDir = new File("src/test/resources/projects/$project")

        if (!projectSourceDir.exists()) {
            throw new FileNotFoundException("Failed to find project directory at: $projectSourceDir.absolutePath")
        }

        // Cleanup some basic things if they already exists
        new File(projectDir, "src").deleteDir()
        new File(projectDir, "build.gradle").delete()
        new File(projectDir, "settings.gradle").delete()

        projectSourceDir.eachFileRecurse { file ->
            if (file.isDirectory()) {
                return
            }

            def path = file.path.replace(projectSourceDir.path, "")

            File tempFile = new File(projectDir, path)

            if (tempFile.exists()) {
                tempFile.delete()
            }

            tempFile.parentFile.mkdirs()
            tempFile.bytes = file.bytes
        }
    }

    @Immutable
    static class GradleProject {
        private String gradleVersion
        private String projectDir
        private String gradleHomeDir

        private String warningMode

        BuildResult run(Map options) {
            // Setup the system props to tell loom that its running in a test env
            // And override the CI check to ensure that everything is ran
            System.setProperty("fabric.loom.test", "true")
            System.setProperty("fabric.loom.ci", "false")
            System.setProperty("maven.repo.local", new File(getGradleHomeDir(), "m2").absolutePath)

            def runner = this.runner
            def args = []

            if (options.task) {
                args << options.task
            }

            args.addAll(options.tasks ?: [])

            args << "--stacktrace"
            args << "--warning-mode" << warningMode
            args << "--gradle-user-home" << gradleHomeDir
            args.addAll(options.args ?: [])

            runner.withArguments(args as String[])

            return options.expectFailure ? runner.buildAndFail() : runner.build()
        }

        private GradleRunner getRunner() {
            return GradleRunner.create()
                    .withProjectDir(getProjectDir())
                    .withPluginClasspath()
                    .withGradleVersion(gradleVersion)
                    .forwardOutput()
                    .withDebug(true)
        }

        File getProjectDir() {
            return new File(projectDir)
        }

        File getGradleHomeDir() {
            return new File(gradleHomeDir)
        }

        File getOutputFile(String filename) {
            return new File(getProjectDir(), "build/libs/$filename")
        }

        void printOutputFiles() {
            new File(getProjectDir(), "build/libs/").listFiles().each {
                println(it.name)
            }
        }

        File getBuildGradle() {
            return new File(getProjectDir(), "build.gradle")
        }

        String getOutputZipEntry(String filename, String entryName) {
            def file = getOutputFile(filename)
            def bytes = ZipUtils.unpackNullable(file.toPath(), entryName)

            if (bytes == null) {
                throw new FileNotFoundException("Could not find ${entryName} in ${entryName}")
            }

            new String(bytes as byte[])
        }

        boolean hasOutputZipEntry(String filename, String entryName) {
            def file = getOutputFile(filename)
            return ZipUtils.unpackNullable(file.toPath(), entryName) != null
        }

        File getGeneratedSources(String mappings) {
            return new File(getGradleHomeDir(), "caches/fabric-loom/${mappings}/minecraft-mapped-sources.jar")
        }
    }
}