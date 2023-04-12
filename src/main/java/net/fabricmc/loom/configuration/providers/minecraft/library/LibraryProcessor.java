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

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.gradle.api.artifacts.dsl.RepositoryHandler;

import net.fabricmc.loom.util.Platform;

public abstract class LibraryProcessor {
	protected static final Predicate<Library> ALLOW_ALL = library -> true;

	protected final Platform platform;
	protected final LibraryContext context;

	public LibraryProcessor(Platform platform, LibraryContext context) {
		this.platform = Objects.requireNonNull(platform);
		this.context = Objects.requireNonNull(context);
	}

	public abstract ApplicationResult getApplicationResult();

	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		return ALLOW_ALL;
	}

	public void applyRepositories(RepositoryHandler repositories) {
	}

	public enum ApplicationResult {
		/**
		 * This processor should be applied automatically to enable Minecraft to run on the current platform.
		 */
		MUST_APPLY,
		/**
		 * This processor can optionally be applied on the current platform.
		 */
		CAN_APPLY,
		/**
		 * This processor is incompatible with the current platform and should not be applied on the current platform.
		 */
		DONT_APPLY
	}
}
