/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util.gradle;

import java.lang.reflect.Method;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.util.GradleVersion;

// This is used to bridge the gap over large gradle api changes.
public class GradleSupport {
	public static final boolean IS_GRADLE_7_OR_NEWER = isIsGradle7OrNewer();

	public static RegularFileProperty getfileProperty(Project project) {
		try {
			// First try the new method, if that fails fall back.
			return getfilePropertyModern(project);
		} catch (Exception e) {
			// Nope
		}

		try {
			return getfilePropertyLegacy(project);
		} catch (Exception e) {
			throw new RuntimeException("Failed to find file property", e);
		}
	}

	private static RegularFileProperty getfilePropertyModern(Project project) throws Exception {
		return getfilePropertyLegacyFromObject(project.getObjects());
	}

	private static RegularFileProperty getfilePropertyLegacy(Project project) throws Exception {
		return getfilePropertyLegacyFromObject(project.getLayout());
	}

	private static RegularFileProperty getfilePropertyLegacyFromObject(Object object) throws Exception {
		Method method = object.getClass().getDeclaredMethod("fileProperty");
		method.setAccessible(true);
		return (RegularFileProperty) method.invoke(object);
	}

	public static boolean isIsGradle7OrNewer() {
		String version = GradleVersion.current().getVersion();
		return Integer.parseInt(version.substring(0, version.indexOf("."))) >= 7;
	}
}
