/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.task.service;

import java.io.Serializable;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.util.GradleVersion;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class JarManifestService implements BuildService<JarManifestService.Params> {
	interface Params extends BuildServiceParameters {
		Property<String> getGradleVersion();
		Property<String> getLoomVersion();
		Property<String> getMCEVersion();
		Property<String> getMinecraftVersion();
		Property<String> getTinyRemapperVersion();
		Property<String> getFabricLoaderVersion();
		Property<MixinVersion> getMixinVersion();
	}

	public static Provider<JarManifestService> get(Project project) {
		return project.getGradle().getSharedServices().registerIfAbsent("LoomJarManifestService:" + project.getName(), JarManifestService.class, spec -> {
			spec.parameters(params -> {
				LoomGradleExtension extension = LoomGradleExtension.get(project);
				Optional<String> tinyRemapperVersion = Optional.ofNullable(TinyRemapper.class.getPackage().getImplementationVersion());

				params.getGradleVersion().set(GradleVersion.current().getVersion());
				params.getLoomVersion().set(LoomGradlePlugin.LOOM_VERSION);
				params.getMCEVersion().set(Constants.Dependencies.Versions.MIXIN_COMPILE_EXTENSIONS);
				params.getMinecraftVersion().set(extension.getMinecraftProvider().minecraftVersion());
				params.getTinyRemapperVersion().set(tinyRemapperVersion.orElse("unknown"));
				params.getFabricLoaderVersion().set(getLoaderVersion(project).orElse("unknown"));
				params.getMixinVersion().set(getMixinVersion(project).orElse(new MixinVersion("unknown", "unknown")));
			});
		});
	}

	public void apply(Manifest manifest) {
		// Don't set when running the reproducible build tests as it will break them when anything updates
		if (Boolean.getBoolean("loom.test.reproducible")) {
			return;
		}

		Attributes attributes = manifest.getMainAttributes();
		Params p = getParameters();

		attributes.putValue("Fabric-Gradle-Version", p.getGradleVersion().get());
		attributes.putValue("Fabric-Loom-Version", p.getLoomVersion().get());
		attributes.putValue("Fabric-Mixin-Compile-Extensions-Version", p.getMCEVersion().get());
		attributes.putValue("Fabric-Minecraft-Version", p.getMinecraftVersion().get());
		attributes.putValue("Fabric-Tiny-Remapper-Version", p.getTinyRemapperVersion().get());
		attributes.putValue("Fabric-Loader-Version", p.getFabricLoaderVersion().get());

		// This can be overridden by mods if required
		if (!attributes.containsKey("Fabric-Mixin-Version")) {
			attributes.putValue("Fabric-Mixin-Version", p.getMixinVersion().get().version());
			attributes.putValue("Fabric-Mixin-Group", p.getMixinVersion().get().group());
		}
	}

	private static Optional<String> getLoaderVersion(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.getInstallerData() == null) {
			project.getLogger().warn("Could not determine fabric loader version for jar manifest");
			return Optional.empty();
		}

		return Optional.of(extension.getInstallerData().version());
	}

	private record MixinVersion(String group, String version) implements Serializable { }

	private static Optional<MixinVersion> getMixinVersion(Project project) {
		// Not super ideal that this uses the mod compile classpath, should prob look into making this not a thing at somepoint
		Optional<Dependency> dependency = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES)
				.getDependencies()
				.stream()
				.filter(dep -> "sponge-mixin".equals(dep.getName()))
				.findFirst();

		if (dependency.isEmpty()) {
			project.getLogger().warn("Could not determine Mixin version for jar manifest");
		}

		return dependency.map(d -> new MixinVersion(d.getGroup(), d.getVersion()));
	}
}
