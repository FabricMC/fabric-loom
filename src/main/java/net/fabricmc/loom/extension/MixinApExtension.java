/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2017 FabricMC
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

package net.fabricmc.loom.extension;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.MixinApExtensionAPI;

/**
 * A gradle extension to configure mixin annotation processor.
 */
@ApiStatus.Experimental
public interface MixinApExtension extends MixinApExtensionAPI {
	String MIXIN_INFORMATION_CONTAINER = "mixin";

	/**
	 * An information container stores necessary information
	 * for configuring the mixin annotation processor. It's stored
	 * in [SourceSet].ext.mixin.
	 */
	final class MixinInformationContainer {
		private final SourceSet sourceSet;
		private final String refmapName;
		private Stream<String> mixinJsonNames;

		final PatternSet mixinJsonPattern;

		public MixinInformationContainer(@NotNull SourceSet sourceSet,
										@NotNull String refmapName,
										@NotNull PatternSet mixinJsonPattern) {
			this.sourceSet = sourceSet;
			this.refmapName = refmapName;
			this.mixinJsonPattern = mixinJsonPattern;
		}

		void setMixinJsonNames(@NotNull Stream<String> mixinJsonNames) {
			if (this.mixinJsonNames == null) {
				this.mixinJsonNames = mixinJsonNames;
			}
		}

		@NotNull
		public Stream<String> getMixinJsonNames() {
			return Objects.requireNonNull(mixinJsonNames);
		}

		@NotNull
		public SourceSet getSourceSet() {
			return sourceSet;
		}

		@NotNull
		public String getRefmapName() {
			return refmapName;
		}
	}

	@Nullable
	static MixinInformationContainer getMixinInformationContainer(SourceSet sourceSet) {
		ExtraPropertiesExtension extra = sourceSet.getExtensions().getExtraProperties();
		return extra.has(MIXIN_INFORMATION_CONTAINER) ? (MixinInformationContainer) extra.get(MIXIN_INFORMATION_CONTAINER) : null;
	}

	static void setMixinInformationContainer(SourceSet sourceSet, MixinInformationContainer container) {
		ExtraPropertiesExtension extra = sourceSet.getExtensions().getExtraProperties();

		if (extra.has(MIXIN_INFORMATION_CONTAINER)) {
			throw new InvalidUserDataException("The sourceSet " + sourceSet.getName()
					+ " has been configured for mixin annotation processor multiple times");
		}

		extra.set(MIXIN_INFORMATION_CONTAINER, container);
	}

	@NotNull
	Stream<SourceSet> getMixinSourceSetsStream();

	@NotNull
	Stream<Configuration> getApConfigurationsStream(Function<String, String> getApConfigNameFunc);

	@NotNull
	Stream<Map.Entry<SourceSet, Task>> getInvokerTasksStream(String compileTaskLanguage);

	@NotNull
	@Input
	Collection<SourceSet> getMixinSourceSets();

	void init();
}
