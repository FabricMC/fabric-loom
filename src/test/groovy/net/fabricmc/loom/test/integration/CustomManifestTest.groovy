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

package net.fabricmc.loom.test.integration

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CustomManifestTest extends Specification implements GradleProjectTestTrait {
    @Unroll
    def "customManifest (gradle #version)"() {
        setup:
            def gradle = gradleProject(project: "minimalBase", version: version)
            gradle.buildGradle << '''
                loom {
                    customMinecraftManifest = "https://maven.fabricmc.net/net/minecraft/1_18_experimental-snapshot-1.json"
                }

                dependencies {
                    minecraft "com.mojang:minecraft:1.18_experimental-snapshot-1"
                    mappings "net.fabricmc:yarn:1.18_experimental-snapshot-1+build.2:v2"
                    modImplementation "net.fabricmc:fabric-loader:0.11.6"
                }
            '''
        when:
            def result = gradle.run(task: "build")

        then:
            result.task(":build").outcome == SUCCESS

        where:
            version << STANDARD_TEST_VERSIONS
    }
}
