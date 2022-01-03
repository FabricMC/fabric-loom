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

package net.fabricmc.loom.configuration.providers.minecraft.mapped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.SignatureFixerApplyVisitor;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class AbstractMappedMinecraftProvider<M extends MinecraftProvider> implements MappedMinecraftProvider.ProviderImpl {
	protected final M minecraftProvider;
	private final Project project;
	protected final LoomGradleExtension extension;

	public AbstractMappedMinecraftProvider(Project project, M minecraftProvider) {
		this.project = project;
		this.minecraftProvider = minecraftProvider;
		this.extension = LoomGradleExtension.get(project);
	}

	public abstract MappingsNamespace getTargetNamespace();

	public abstract List<RemappedJars> getRemappedJars();

	protected void applyDependencies(BiConsumer<String, String> consumer) {
		// Override if needed
	}

	public void provide(boolean applyDependencies) throws Exception {
		final List<RemappedJars> remappedJars = getRemappedJars();
		assert !remappedJars.isEmpty();

		if (!areOutputsValid(remappedJars) || LoomGradlePlugin.refreshDeps) {
			try {
				remapInputs(remappedJars);
			} catch (Throwable t) {
				cleanOutputs(remappedJars);

				throw new RuntimeException("Failed to remap minecraft", t);
			}
		}

		if (applyDependencies) {
			applyDependencies((configuration, name) -> getProject().getDependencies().add(configuration, getDependencyNotation(name)));
		}
	}

	protected abstract Path getDirectory();

	@Override
	public Path getJar(String name) {
		return getDirectory().resolve(getName(name) + ".jar");
	}

	protected String getName(String name) {
		return "minecraft-%s-%s".formatted(name, getTargetNamespace().toString());
	}

	protected String getDependencyNotation(String name) {
		return "net.minecraft:%s:%s/%s".formatted(getName(name), extension.getMinecraftProvider().minecraftVersion(), extension.getMappingsProvider().mappingsIdentifier());
	}

	private boolean areOutputsValid(List<RemappedJars> remappedJars) {
		for (RemappedJars remappedJar : remappedJars) {
			if (!Files.exists(remappedJar.outputJar())) {
				return false;
			}
		}

		return true;
	}

	private void remapInputs(List<RemappedJars> remappedJars) throws IOException {
		cleanOutputs(remappedJars);

		for (RemappedJars remappedJar : remappedJars) {
			remapJar(remappedJar);
		}
	}

	private void remapJar(RemappedJars remappedJars) throws IOException {
		final MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
		final String fromM = remappedJars.sourceNamespace().toString();
		final String toM = getTargetNamespace().toString();

		Files.deleteIfExists(remappedJars.outputJar());

		final Map<String, String> remappedSignatures = SignatureFixerApplyVisitor.getRemappedSignatures(getTargetNamespace() == MappingsNamespace.INTERMEDIARY, mappingsProvider, project, toM);
		TinyRemapper remapper = TinyRemapperHelper.getTinyRemapper(project, fromM, toM, true, (builder) -> {
			builder.extraPostApplyVisitor(new SignatureFixerApplyVisitor(remappedSignatures));
			configureRemapper(remappedJars, builder);
		});

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedJars.outputJar()).build()) {
			outputConsumer.addNonClassFiles(remappedJars.inputJar());
			remapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(project));

			for (Path path : remappedJars.remapClasspath()) {
				remapper.readClassPath(path);
			}

			remapper.readInputs(remappedJars.inputJar());
			remapper.apply(outputConsumer);
		} catch (Exception e) {
			throw new RuntimeException("Failed to remap JAR " + remappedJars.inputJar() + " with mappings from " + mappingsProvider.tinyMappings, e);
		} finally {
			remapper.finish();
		}
	}

	protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
	}

	private void cleanOutputs(List<RemappedJars> remappedJars) throws IOException {
		for (RemappedJars remappedJar : remappedJars) {
			Files.deleteIfExists(remappedJar.outputJar());
		}
	}

	public Project getProject() {
		return project;
	}

	public M getMinecraftProvider() {
		return minecraftProvider;
	}

	public record RemappedJars(Path inputJar, Path outputJar, MappingsNamespace sourceNamespace, Path... remapClasspath) {
	}
}
