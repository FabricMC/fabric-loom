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

package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class RuntimeLog4jLibraryProcessor extends LibraryProcessor {
	private static final String LOG4J_GROUP = "org.apache.logging.log4j";

	public RuntimeLog4jLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		return ApplicationResult.CAN_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		return library -> {
			if (library.is(LOG4J_GROUP)) {
				dependencyConsumer.accept(library.withTarget(Library.Target.RUNTIME));
				return false;
			}

			return true;
		};
	}
}
