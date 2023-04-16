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

import org.jetbrains.annotations.Nullable;

public record Library(String group, String name, String version, @Nullable String classifier, Target target) {
	public enum Target {
		/**
		 * A runtime only library.
		 */
		RUNTIME,
		/**
		 * A runtime and compile library.
		 */
		COMPILE,
		/**
		 * Natives.
		 */
		NATIVES,
		/**
		 * A mod library that needs remapping.
		 */
		LOCAL_MOD
	}

	public static Library fromMaven(String name, Target target) {
		String[] split = name.split(":");
		assert split.length == 3 || split.length == 4;

		return new Library(split[0], split[1], split[2], split.length == 4 ? split[3] : null, target);
	}

	/**
	 * Returns true when the group or the group and name match.
	 *
	 * @param str Takes a string containing the maven group, or a group and name split by :
	 * @return true when the group or the group and name match.
	 */
	public boolean is(String str) {
		if (str.contains(":")) {
			final String[] split = str.split(":");
			assert split.length == 2;
			return this.group.equals(split[0]) && this.name.equals(split[1]);
		}

		return this.group.equals(str);
	}

	public String mavenNotation() {
		if (classifier != null) {
			return "%s:%s:%s:%s".formatted(group, name, version, classifier);
		}

		return "%s:%s:%s".formatted(group, name, version);
	}

	public Library withVersion(String version) {
		return new Library(this.group, this.name, version, this.classifier, this.target);
	}

	public Library withClassifier(@Nullable String classifier) {
		return new Library(this.group, this.name, this.version, classifier, this.target);
	}

	public Library withTarget(Target target) {
		return new Library(this.group, this.name, this.version, this.classifier, target);
	}
}
