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

package net.fabricmc.loom.api.remapping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * A remapper extension, that has direct access to the TinyRemapper APIs.
 *
 * <p>This API is not stable and may change without notice.
 */
@ApiStatus.Experimental
public interface TinyRemapperExtension {
	/**
	 * See: {@link TinyRemapper.Builder#extraAnalyzeVisitor(TinyRemapper.AnalyzeVisitorProvider)}.
	 *
	 * @return A {@link TinyRemapper.AnalyzeVisitorProvider} or {@code null}.
	 */
	@Nullable
	default TinyRemapper.AnalyzeVisitorProvider getAnalyzeVisitorProvider(Context context) {
		return getAnalyzeVisitorProvider();
	}

	/**
	 * See: {@link TinyRemapper.Builder#extraPreApplyVisitor(TinyRemapper.ApplyVisitorProvider)}.
	 *
	 * @return A {@link TinyRemapper.ApplyVisitorProvider} or {@code null}.
	 */
	@Nullable
	default TinyRemapper.ApplyVisitorProvider getPreApplyVisitor(Context context) {
		return getPreApplyVisitor();
	}

	/**
	 * See: {@link TinyRemapper.Builder#extraPostApplyVisitor(TinyRemapper.ApplyVisitorProvider)}.
	 *
	 * @return A {@link TinyRemapper.ApplyVisitorProvider} or {@code null}.
	 */
	@Nullable
	default TinyRemapper.ApplyVisitorProvider getPostApplyVisitor(Context context) {
		return getPostApplyVisitor();
	}

	interface Context {
		/**
		 * @return The source namespace.
		 */
		String sourceNamespace();

		/**
		 * @return The target namespace.
		 */
		String targetNamespace();
	}

	// Deprecated, for removal in Loom 1.6:

	/**
	 * @deprecated Use {@link #getAnalyzeVisitorProvider(Context)} instead.
	 */
	@Deprecated(forRemoval = true)
	@Nullable
	default TinyRemapper.AnalyzeVisitorProvider getAnalyzeVisitorProvider() {
		return null;
	}

	/**
	 * @deprecated Use {@link #getPreApplyVisitor(Context)} instead.
	 */
	@Deprecated(forRemoval = true)
	@Nullable
	default TinyRemapper.ApplyVisitorProvider getPreApplyVisitor() {
		return null;
	}

	/**
	 * @deprecated Use {@link #getPostApplyVisitor(Context)} instead.
	 */
	@Deprecated(forRemoval = true)
	@Nullable
	default TinyRemapper.ApplyVisitorProvider getPostApplyVisitor() {
		return null;
	}
}
