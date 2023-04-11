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

package net.fabricmc.loom.configuration.providers.minecraft.library;

public interface LibraryContext {
	/**
	 * @return True when the Minecraft libraries support ARM64 MacOS
	 */
	boolean supportsArm64MacOS();

	/**
	 * @return True when the Minecraft libraries support Java 19 or later
	 */
	boolean supportsJava19OrLater();

	/**
	 * @return True when using LWJGL 3
	 */
	boolean usesLWJGL3();

	/**
	 * @return True when the Minecraft natives are on the classpath, as opposed to being extracted
	 */
	boolean hasClasspathNatives();

	/**
	 * @return True when there is an exact match for this library
	 */
	boolean hasLibrary(String name);

	/**
	 * @return True when the current Java version is 19 or later
	 */
	boolean isJava19OrLater();
}
