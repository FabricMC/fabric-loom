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

import javax.inject.Inject;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassVisitor;

/**
 * A remapper extension can be used to add extra processing to the remapping process.
 *
 * <p>Implementations of RemapperExtension's are subject to the following constraints:
 * <ul>
 *     <li>Do not implement {@link #getParameters()} in your class, the method will be implemented by Gradle.</li>
 *     <li>Constructors must be annotated with {@link Inject}.</li>
 * </ul>
 *
 * @param <T> Parameter type for the extension. Should be {@link RemapperParameters.None} if the action does not have parameters.
 */
public interface RemapperExtension<T extends RemapperParameters> {
	/**
	 * @return The parameters for this extension.
	 */
	@ApiStatus.NonExtendable
	@Inject
	T getParameters();

	/**
	 * Return a {@link ClassVisitor} that will be used when remapping the given class.
	 *
	 * @param className The name of the class being remapped
	 * @param remapperContext The remapper context
	 * @param classVisitor The parent class visitor
	 * @return A {@link ClassVisitor} that will be used when remapping the given class, or the given {@code classVisitor} if no extra processing is required for this class.
	 */
	ClassVisitor insertVisitor(String className, RemapperContext remapperContext, ClassVisitor classVisitor);
}
