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

package net.fabricmc.loom.util;

public class Version {

	private String mappingsVersion;
	private String minecraftVersion;

	private String version;

	public Version(String version) {
		this.version = version;

		if(version.contains("+build.")){
			this.minecraftVersion = version.substring(0, version.lastIndexOf('+'));
			this.mappingsVersion = version.substring(version.lastIndexOf('.') + 1);
		} else {
			//TODO legacy remove when no longer needed
			char verSep = version.contains("-") ? '-' : '.';
			this.minecraftVersion = version.substring(0, version.lastIndexOf(verSep));
			this.mappingsVersion = version.substring(version.lastIndexOf(verSep) + 1);
		}
	}

	public String getMappingsVersion() {
		return mappingsVersion;
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}

	@Override
	public String toString() {
		return version;
	}
}