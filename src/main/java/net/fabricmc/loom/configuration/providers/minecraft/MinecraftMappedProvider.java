/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public class MinecraftMappedProvider extends DependencyProvider {
	private File minecraftMappedJar;
	private File minecraftIntermediaryJar;

	private MinecraftProviderImpl minecraftProvider;

	public MinecraftMappedProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		if (Files.notExists(getExtension().getMappingsProvider().tinyMappings)) {
			throw new RuntimeException("mappings file not found");
		}

		if (!getExtension().getMinecraftProvider().getMergedJar().exists()) {
			throw new RuntimeException("input merged jar not found");
		}

		if (!minecraftMappedJar.exists() || !getIntermediaryJar().exists() || isRefreshDeps()) {
			if (minecraftMappedJar.exists()) {
				minecraftMappedJar.delete();
			}

			minecraftMappedJar.getParentFile().mkdirs();

			if (minecraftIntermediaryJar.exists()) {
				minecraftIntermediaryJar.delete();
			}

			try {
				mapMinecraftJar();
			} catch (Throwable t) {
				// Cleanup some some things that may be in a bad state now
				minecraftMappedJar.delete();
				minecraftIntermediaryJar.delete();
				getExtension().getMappingsProvider().cleanFiles();
				throw new RuntimeException("Failed to remap minecraft", t);
			}
		}

		if (!minecraftMappedJar.exists()) {
			throw new RuntimeException("mapped jar not found");
		}

		addDependencies(dependency, postPopulationScheduler);
	}

	private void mapMinecraftJar() throws IOException {
		String fromM = MappingsNamespace.OFFICIAL.toString();

		MappingsProviderImpl mappingsProvider = getExtension().getMappingsProvider();

		Path input = minecraftProvider.getMergedJar().toPath();
		Path outputMapped = minecraftMappedJar.toPath();
		Path outputIntermediary = minecraftIntermediaryJar.toPath();

		for (String toM : Arrays.asList(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString())) {
			final boolean toNamed = MappingsNamespace.NAMED.toString().equals(toM);
			final boolean toIntermediary = MappingsNamespace.INTERMEDIARY.toString().equals(toM);
			final boolean fixSignatures = mappingsProvider.getSignatureFixes() != null;
			final Path output = toNamed ? outputMapped : outputIntermediary;

			getProject().getLogger().lifecycle(":remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ")");

			Files.deleteIfExists(output);

			// Bit ugly but whatever, the whole issue is a bit ugly :)
			AtomicReference<Map<String, String>> remappedSignatures = new AtomicReference<>();
			TinyRemapper remapper = TinyRemapperHelper.getTinyRemapper(getProject(), fromM, toM, true, (builder) -> {
				if (!fixSignatures) {
					return;
				}

				builder.extraPostApplyVisitor(new TinyRemapper.ApplyVisitorProvider() {
					@Override
					public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
						return new ClassVisitor(Constants.ASM_VERSION, next) {
							@Override
							public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
								Map<String, String> signatureFixes = Objects.requireNonNull(remappedSignatures.get(), "Could not get remapped signatures");

								if (signature == null) {
									signature = signatureFixes.getOrDefault(name, null);

									if (signature != null) {
										getProject().getLogger().info("Replaced signature for {} with {}", name, signature);
									}
								}

								super.visit(version, access, name, signature, superName, interfaces);
							}
						};
					}
				});
			});

			if (fixSignatures) {
				if (toIntermediary) {
					// No need to remap, as these are already intermediary
					remappedSignatures.set(mappingsProvider.getSignatureFixes());
				} else {
					// Remap the sig fixes from intermediary to the target namespace
					final Map<String, String> remapped = new HashMap<>();
					final TinyRemapper sigTinyRemapper = TinyRemapperHelper.getTinyRemapper(getProject(), MappingsNamespace.INTERMEDIARY.toString(), toM);
					final Remapper sigAsmRemapper = sigTinyRemapper.getRemapper();

					// Remap the class names and the signatures using a new tiny remapper instance.
					for (Map.Entry<String, String> entry : mappingsProvider.getSignatureFixes().entrySet()) {
						remapped.put(
								sigAsmRemapper.map(entry.getKey()),
								sigAsmRemapper.mapSignature(entry.getValue(), false)
						);
					}

					sigTinyRemapper.finish();
					remappedSignatures.set(remapped);
				}
			}

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
				outputConsumer.addNonClassFiles(input);
				remapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(getProject()));
				remapper.readInputs(input);
				remapper.apply(outputConsumer);
			} catch (Exception e) {
				throw new RuntimeException("Failed to remap JAR " + input + " with mappings from " + mappingsProvider.tinyMappings, e);
			} finally {
				remapper.finish();
			}
		}
	}

	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED,
				getProject().getDependencies().module("net.minecraft:minecraft-mapped:" + getMinecraftProvider().minecraftVersion() + "/" + getExtension().getMappingsProvider().mappingsIdentifier()));
	}

	public void initFiles(MinecraftProviderImpl minecraftProvider, MappingsProviderImpl mappingsProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftIntermediaryJar = new File(getExtension().getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-intermediary.jar");
		minecraftMappedJar = new File(getExtension().getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-mapped.jar");
	}

	protected File getJarDirectory(File parentDirectory, String type) {
		return new File(parentDirectory, getJarVersionString(type));
	}

	protected String getJarVersionString(String type) {
		return String.format("%s-%s", type, getExtension().getMappingsProvider().mappingsIdentifier());
	}

	public File getIntermediaryJar() {
		return minecraftIntermediaryJar;
	}

	public File getMappedJar() {
		return minecraftMappedJar;
	}

	public final File getBaseMappedJar() {
		return minecraftMappedJar;
	}

	public File getUnpickedJar() {
		return new File(getExtension().getMappingsProvider().mappingsWorkingDir().toFile(), "minecraft-unpicked.jar");
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT_NAMED;
	}
}
