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

package net.fabricmc.loom.configuration;

import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;

public final record JarManifestConfiguration(Project project) {
	public void configure(Manifest manifest) {
		// Dont set when running the reproducible build tests as it will break them when anything updates
		if (Boolean.getBoolean("loom.test.reproducible")) {
			return;
		}

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		Attributes attributes = manifest.getMainAttributes();
		var tinyRemapperVersion = Optional.ofNullable(TinyRemapper.class.getPackage().getImplementationVersion());

		attributes.putValue("Fabric-Gradle-Version", GradleVersion.current().getVersion());
		attributes.putValue("Fabric-Loom-Version", LoomGradlePlugin.LOOM_VERSION);
		attributes.putValue("Fabric-Mixin-Compile-Extensions-Version", Constants.Dependencies.Versions.MIXIN_COMPILE_EXTENSIONS);
		attributes.putValue("Fabric-Minecraft-Version", extension.getMinecraftProvider().minecraftVersion());
		tinyRemapperVersion.ifPresent(s -> attributes.putValue("Fabric-Tiny-Remapper-Version", s));
		getLoaderVersion().ifPresent(s -> attributes.putValue("Fabric-Loader-Version", s));

		// This can be overridden by mods if required
		if (!attributes.containsKey("Fabric-Mixin-Version")) {
			addMixinVersion(attributes);
		}
	}

	private void addMixinVersion(Attributes attributes) {
		// Not super ideal that this uses the mod compile classpath, should prob look into making this not a thing at somepoint
		var dependency = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES)
				.getDependencies()
				.stream()
				.filter(dep -> "sponge-mixin".equals(dep.getName()))
				.findFirst();

		if (dependency.isEmpty()) {
			project.getLogger().warn("Could not determine Mixin version for jar manifest");
			return;
		}

		attributes.putValue("Fabric-Mixin-Version", dependency.get().getVersion());
		attributes.putValue("Fabric-Mixin-Group", dependency.get().getGroup());
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
