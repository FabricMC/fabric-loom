/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2017 FabricMC
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

import java.util.jar.JarFile

import static net.fabricmc.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MixinApSimpleTest extends Specification implements GradleProjectTestTrait {
    @Unroll
    def "build (gradle #version)"() {
        setup:
            def gradle = gradleProject(project: "mixinApSimple", version: version)

        when:
            def result = gradle.run(task: "build")

        then:
            result.task(":build").outcome == SUCCESS

            // verify the ref-map name is correctly generated
            def main = new JarFile(gradle.getOutputFile("fabric-example-mod-1.0.0-dev.jar").absoluteFile)
            main.getEntry("main-refmap0000.json") != null
            def mixin = new JarFile(gradle.getOutputFile("fabric-example-mod-1.0.0-mixin.jar").absoluteFile)
            mixin.getEntry("default-refmap0000.json") != null
            def mixin1 = new JarFile(gradle.getOutputFile("fabric-example-mod-1.0.0-mixin1.jar").absoluteFile)
            mixin1.getEntry("main-refmap0000.json") == null
            mixin1.getEntry("default-refmap0000.json") == null

        where:
            version << STANDARD_TEST_VERSIONS
    }
}
