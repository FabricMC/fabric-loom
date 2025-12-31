/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.processors.speccontext;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.RemapConfigurationSettings;

public interface RemappedProjectView extends ProjectView {
	Function<RemapConfigurationSettings, Stream<Path>> resolveArtifacts(ArtifactUsage artifactUsage);

	NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations();

	List<RemapConfigurationSettings> getCompileRemapConfigurations();

	List<RemapConfigurationSettings> getRuntimeRemapConfigurations();

	MappingsNamespace getProductionNamespace();

	class Impl extends AbstractProjectView implements RemappedProjectView {
		public Impl(Project project) {
			super(project);
		}

		@Override
		public Function<RemapConfigurationSettings, Stream<Path>> resolveArtifacts(ArtifactUsage artifactUsage) {
			final Usage usage = project.getObjects().named(Usage.class, artifactUsage.getGradleUsage());

			return settings -> {
				final Configuration configuration = settings.getSourceConfiguration().get().copyRecursive();
				configuration.setCanBeConsumed(false);
				configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));
				return configuration.resolve().stream().map(File::toPath);
			};
		}

		@Override
		public NamedDomainObjectList<RemapConfigurationSettings> getRemapConfigurations() {
			return extension.getRemapConfigurations();
		}

		@Override
		public List<RemapConfigurationSettings> getCompileRemapConfigurations() {
			return extension.getCompileRemapConfigurations();
		}

		@Override
		public List<RemapConfigurationSettings> getRuntimeRemapConfigurations() {
			return extension.getRuntimeRemapConfigurations();
		}

		@Override
		public MappingsNamespace getProductionNamespace() {
			return extension.getProductionNamespaceEnum().get();
		}
	}
}
