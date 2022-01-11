/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.api.decompilers;

import java.io.Serializable;
import java.util.Map;

import com.google.common.base.Preconditions;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public abstract class DecompilerOptions implements Named {
	/**
	 * Class name for to the {@link LoomDecompiler}.
	 */
	public abstract Property<String> getDecompilerClassName();

	/**
	 * Additional classpath entries for the decompiler jvm.
	 */
	public abstract ConfigurableFileCollection getClasspath();

	/**
	 * Additional options to be passed to the decompiler.
	 */
	public abstract MapProperty<String, String> getOptions();

	/**
	 * Memory used for forked JVM in megabytes.
	 */
	public abstract Property<Long> getMemory();

	/**
	 * Maximum number of threads the decompiler is allowed to use.
	 */
	public abstract Property<Integer> getMaxThreads();

	public DecompilerOptions() {
		getDecompilerClassName().finalizeValueOnRead();
		getClasspath().finalizeValueOnRead();
		getOptions().finalizeValueOnRead();
		getMemory().convention(4096L).finalizeValueOnRead();
		getMaxThreads().convention(Runtime.getRuntime().availableProcessors()).finalizeValueOnRead();
	}

	// Done to work around weird issues with the workers, possibly https://github.com/gradle/gradle/issues/13422
	public record Dto(String className, Map<String, String> options, int maxThreads) implements Serializable { }

	public Dto toDto() {
		Preconditions.checkArgument(getDecompilerClassName().isPresent(), "No decompiler classname specified for decompiler: " + getName());
		return new Dto(
				getDecompilerClassName().get(),
				getOptions().get(),
				getMaxThreads().get()
		);
	}
}
