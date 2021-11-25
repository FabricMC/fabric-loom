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
import com.google.gson.JsonParser;
import java.util.jar.JarFile

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class MixinApAutoRefmapTest extends Specification implements GradleProjectTestTrait {
    @Unroll
    def "build (gradle #version)"() {
        setup:
            def gradle = gradleProject(project: "mixinApAutoRefmap", version: version)

        when:
            def result = gradle.run(task: "build")

        then:
            result.task(":build").outcome == SUCCESS

            // verify the ref-map name is correctly generated
            def jar = new JarFile(gradle.getOutputFile("fabric-example-mod-1.0.0-universal.jar").absoluteFile)
            jar.getEntry("refmap0000.json") == null
            jar.getEntry("refmap0001.json") != null
            jar.getEntry("refmap0002.json") != null
            jar.getEntry("refmap0003.json") != null

            def j1 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("main.mixins.json"))))
            j1.asJsonObject.getAsJsonPrimitive("refmap").getAsString() == "refmap0001.json"

            def j2 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("blabla.json"))))
            j2.asJsonObject.getAsJsonPrimitive("refmap").getAsString() == "refmap0002.json"

            def j3 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("m1_1.mixins.json"))))
            j3.asJsonObject.getAsJsonPrimitive("refmap").getAsString() == "refmap0003.json"

            def j4 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("m1_2.mixins.json"))))
            !j4.asJsonObject.has("refmap")

            def j5 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("irrelevant.mixins.json"))))
            !j5.asJsonObject.has("refmap")

            def j6 = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(jar.getEntry("subfolder/subfolder.mixins.json"))))
            j6.asJsonObject.getAsJsonPrimitive("refmap").getAsString() == "refmap0001.json"

        where:
            version << STANDARD_TEST_VERSIONS
    }
}
