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

package net.fabricmc.loom.configuration;

import java.util.Optional;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.java.archives.Attributes;
import org.gradle.jvm.tasks.Jar;
import org.gradle.util.GradleVersion;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.Constants;

public final record JarManifestConfiguration(Project project, Jar jar) {
	public void configure() {
		// Done in doFirst to ensure that everyone else has had time to modify the manifest
		// and to ensure that the compile classpath has already been resolved.
		jar.doFirst(configureManifestAction());
	}

	private Action<Task> configureManifestAction() {
		//noinspection Convert2Lambda
		return new Action<Task>() {
			@Override
			public void execute(Task task) {
				jar.manifest(manifest -> {
					Attributes attributes = manifest.getAttributes();

					attributes.put("Loom-Version", LoomGradlePlugin.LOOM_VERSION);
					attributes.put("Gradle-Version", GradleVersion.current().getVersion());
					attributes.put("Mixin-Compile-Extensions-Version", Constants.Dependencies.Versions.MIXIN_COMPILE_EXTENSIONS);
					attributes.put("Tiny-Remapper-Version", "TODO"); // TODO this needs to be the version used by gradle/loom
					getLoaderVersion().ifPresent(s -> attributes.put("Fabric-Loader-Version", s));

					// This can be overridden by mods if required
					if (!attributes.containsKey("Mixin-Version")) {
						addMixinVersion(attributes);
					}
				});
			}
		};
	}

	private void addMixinVersion(Attributes attributes) {
		// Not super ideal that this uses the mod compile classpath, should prob look into making this not a thing at somepoint
		var version = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES)
				.getDependencies()
				.stream()
				.filter(dependency -> "sponge-mixin".equals(dependency.getName()))
				.map(Dependency::getVersion)
				.findFirst();

		if (version.isEmpty()) {
			project.getLogger().warn("Could not determine Mixin version for jar manifest");
			return;
		}

		attributes.put("Mixin-Version", version.get());
	}

	private Optional<String> getLoaderVersion() {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		if (extension.getInstallerData() == null) {
			project.getLogger().warn("Could not determine fabric loader version for jar manifest");
			return Optional.empty();
		}

		return Optional.of(extension.getInstallerData().version());
	}
}
