/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.unit

import org.gradle.api.Project
import spock.lang.Specification

import net.fabricmc.loom.configuration.fabricapi.FabricApiVersions
import net.fabricmc.loom.test.util.GradleTestUtil

class FabricApiExtensionTest extends Specification {
	def "get module version"() {
		when:
		def fabricApi = new FabricApiVersions() {
					Project project = GradleTestUtil.mockProject()
				}
		def version = fabricApi.moduleVersion(moduleName, apiVersion)

		then:
		version == expectedVersion

		where:
		moduleName             | apiVersion              | expectedVersion
		"fabric-api-base"      | "0.88.3+1.20.2"         | "0.4.32+fce67b3299"  // Normal module, new version
		"fabric-api-base"      | "0.13.1+build.257-1.14" | "0.1.2+28f8190f42"   // Normal module, old version before deprecated modules.
		"fabric-networking-v0" | "0.88.0+1.20.1"         | "0.3.50+df3654b377"  // Deprecated module, opt-out version
		"fabric-networking-v0" | "0.85.0+1.20.1"         | "0.3.48+df3654b377"  // Deprecated module, opt-in version
	}

	def "unknown module"() {
		when:
		def fabricApi = new FabricApiVersions() {
					Project project = GradleTestUtil.mockProject()
				}
		fabricApi.moduleVersion("fabric-api-unknown", apiVersion)

		then:
		def e = thrown RuntimeException
		e.getMessage() == "Failed to find module version for module: fabric-api-unknown"

		where:
		apiVersion              | _
		"0.88.0+1.20.1"         | _ // Deprecated opt-out
		"0.85.0+1.20.1"         | _ // Deprecated opt-int
		"0.13.1+build.257-1.14" | _ // No deprecated modules
	}
}