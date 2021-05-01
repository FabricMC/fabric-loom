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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import dev.architectury.tinyremapper.IMappingProvider;
import dev.architectury.tinyremapper.TinyRemapper;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.tr.OutputRemappingHandler;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ThreadingUtils;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.loom.util.srg.AtRemapper;
import net.fabricmc.loom.util.srg.CoreModClassRemapper;
import net.fabricmc.loom.util.srg.InnerClassRemapper;
import net.fabricmc.mapping.tree.TinyTree;

public class MinecraftMappedProvider extends DependencyProvider {
	private static final Map<String, String> JSR_TO_JETBRAINS = new ImmutableMap.Builder<String, String>()
			.put("javax/annotation/Nullable", "org/jetbrains/annotations/Nullable")
			.put("javax/annotation/Nonnull", "org/jetbrains/annotations/NotNull")
			.put("javax/annotation/concurrent/Immutable", "org/jetbrains/annotations/Unmodifiable")
			.build();

	private File inputJar;
	private File minecraftMappedJar;
	private File minecraftIntermediaryJar;
	private File minecraftSrgJar;

	private MinecraftProvider minecraftProvider;

	public MinecraftMappedProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		if (!getExtension().getMappingsProvider().tinyMappings.exists()) {
			throw new RuntimeException("mappings file not found");
		}

		if (!inputJar.exists()) {
			throw new RuntimeException("input merged jar not found");
		}

		boolean isForgeAtDirty = getExtension().isForge() && getExtension().getMappingsProvider().patchedProvider.isAtDirty();

		if (!minecraftMappedJar.exists() || !getIntermediaryJar().exists() || (getExtension().isForge() && !getSrgJar().exists()) || isRefreshDeps() || isForgeAtDirty) {
			if (minecraftMappedJar.exists()) {
				minecraftMappedJar.delete();
			}

			minecraftMappedJar.getParentFile().mkdirs();

			if (minecraftIntermediaryJar.exists()) {
				minecraftIntermediaryJar.delete();
			}

			if (getExtension().isForge() && minecraftSrgJar.exists()) {
				minecraftSrgJar.delete();
			}

			try {
				mapMinecraftJar();
			} catch (Throwable t) {
				// Cleanup some some things that may be in a bad state now
				DownloadUtil.delete(minecraftMappedJar);
				DownloadUtil.delete(minecraftIntermediaryJar);
				getExtension().getMinecraftProvider().deleteFiles();

				if (getExtension().isForge()) {
					minecraftSrgJar.delete();
				}

				getExtension().getMappingsProvider().cleanFiles();
				throw new RuntimeException("Failed to remap minecraft", t);
			}
		}

		if (!minecraftMappedJar.exists()) {
			throw new RuntimeException("mapped jar not found");
		}

		addDependencies(dependency, postPopulationScheduler);
	}

	private void mapMinecraftJar() throws Exception {
		String fromM = "official";

		MappingsProvider mappingsProvider = getExtension().getMappingsProvider();

		Path input = inputJar.toPath();
		Path outputMapped = minecraftMappedJar.toPath();
		Path outputIntermediary = minecraftIntermediaryJar.toPath();
		Path outputSrg = minecraftSrgJar == null ? null : minecraftSrgJar.toPath();

		Path[] libraries = getRemapClasspath(getProject());
		TinyRemapper remapper = getTinyRemapper();
		remapper.readClassPath(libraries);
		remapper.prepareClasses();

		Path tmpAssets = Files.createTempFile("tmpAssets", null);
		Files.deleteIfExists(tmpAssets);
		tmpAssets.toFile().deleteOnExit();

		List<byte[]> inputByteList = new ArrayList<>();

		try (FileSystemUtil.FileSystemDelegate inputFs = FileSystemUtil.getJarFileSystem(input, false)) {
			ThreadingUtils.TaskCompleter taskCompleter = ThreadingUtils.taskCompleter();

			try (FileSystemUtil.FileSystemDelegate assetsFs = FileSystemUtil.getJarFileSystem(tmpAssets, true)) {
				for (Path path : (Iterable<? extends Path>) Files.walk(inputFs.get().getPath("/"))::iterator) {
					if (Files.isRegularFile(path)) {
						if (path.getFileName().toString().endsWith(".class")) {
							taskCompleter.add(() -> {
								byte[] bytes = Files.readAllBytes(path);

								synchronized (inputByteList) {
									inputByteList.add(bytes);
								}
							});
						} else {
							Path p = assetsFs.get().getPath(path.toString());

							if (p.getParent() != null) {
								Files.createDirectories(p.getParent());
							}

							taskCompleter.add(() -> {
								Files.copy(path, p);
							});
						}
					}
				}

				taskCompleter.complete();
			}
		}

		byte[][] inputBytes = inputByteList.toArray(new byte[0][0]);

		for (String toM : getExtension().isForge() ? Arrays.asList("intermediary", "srg", "named") : Arrays.asList("intermediary", "named")) {
			Path output = "named".equals(toM) ? outputMapped : "srg".equals(toM) ? outputSrg : outputIntermediary;
			Stopwatch stopwatch = Stopwatch.createStarted();
			getProject().getLogger().lifecycle(":remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ")");

			remapper.readInputs(inputBytes);
			remapper.replaceMappings(getMappings(input, fromM, toM));
			OutputRemappingHandler.remap(remapper, tmpAssets, output);

			getProject().getLogger().lifecycle(":remapped minecraft (TinyRemapper, " + fromM + " -> " + toM + ") in " + stopwatch);
			remapper.removeInput();

			if (getExtension().isForge() && !"srg".equals(toM)) {
				getProject().getLogger().info(":running forge finalising tasks");

				// TODO: Relocate this to its own class
				try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), ImmutableMap.of("create", false))) {
					Path manifestPath = fs.getPath("META-INF", "MANIFEST.MF");
					Manifest minecraftManifest;
					Manifest forgeManifest;

					try (InputStream in = Files.newInputStream(manifestPath)) {
						minecraftManifest = new Manifest(in);
					}

					try (InputStream in = new FileInputStream(getExtension().getForgeUniversalProvider().getForgeManifest())) {
						forgeManifest = new Manifest(in);
					}

					for (Map.Entry<String, Attributes> forgeEntry : forgeManifest.getEntries().entrySet()) {
						if (forgeEntry.getKey().endsWith("/")) {
							minecraftManifest.getEntries().put(forgeEntry.getKey(), forgeEntry.getValue());
						}
					}

					Files.delete(manifestPath);

					try (OutputStream out = Files.newOutputStream(manifestPath)) {
						minecraftManifest.write(out);
					}
				}

				TinyTree yarnWithSrg = getExtension().getMappingsProvider().getMappingsWithSrg();
				AtRemapper.remap(getProject().getLogger(), output, yarnWithSrg);
				CoreModClassRemapper.remapJar(output, yarnWithSrg, getProject().getLogger());
			}
		}

		remapper.finish();
	}

	public TinyRemapper getTinyRemapper() throws IOException {
		TinyRemapper.Builder builder = TinyRemapper.newRemapper()
				.renameInvalidLocals(true)
				.ignoreConflicts(getExtension().isForge())
				.cacheMappings(true)
				.threads(Runtime.getRuntime().availableProcessors())
				.logger(getProject().getLogger()::lifecycle)
				.rebuildSourceFilenames(true);

		if (getExtension().isForge()) {
			/* FORGE: Required for classes like aej$OptionalNamedTag (1.16.4) which are added by Forge patches.
			 * They won't get remapped to their proper packages, so IllegalAccessErrors will happen without ._.
			 */
			builder.fixPackageAccess(true);
		}

		return builder.build();
	}

	public Set<IMappingProvider> getMappings(@Nullable Path fromJar, String fromM, String toM) throws IOException {
		Set<IMappingProvider> providers = new HashSet<>();
		providers.add(TinyRemapperMappingsHelper.create(getExtension().isForge() ? getExtension().getMappingsProvider().getMappingsWithSrg() : getExtension().getMappingsProvider().getMappings(), fromM, toM, true));

		if (getExtension().isForge()) {
			if (fromJar != null) {
				providers.add(InnerClassRemapper.of(fromJar, getExtension().getMappingsProvider().getMappingsWithSrg(), fromM, toM));
			}
		} else {
			providers.add(out -> JSR_TO_JETBRAINS.forEach(out::acceptClass));
		}

		return providers;
	}

	public static Path[] getRemapClasspath(Project project) {
		return project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).toArray(Path[]::new);
	}

	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		getProject().getRepositories().flatDir(repository -> repository.dir(getJarDirectory(getExtension().getUserCache(), "mapped")));

		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString("mapped")));
	}

	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftIntermediaryJar = new File(getExtension().getUserCache(), "minecraft-" + getJarVersionString("intermediary") + ".jar");
		minecraftSrgJar = !getExtension().isForge() ? null : new File(getExtension().getUserCache(), "minecraft-" + getJarVersionString("srg") + ".jar");
		minecraftMappedJar = new File(getJarDirectory(getExtension().getUserCache(), "mapped"), "minecraft-" + getJarVersionString("mapped") + ".jar");
		inputJar = getExtension().isForge() ? mappingsProvider.patchedProvider.getMergedJar() : minecraftProvider.getMergedJar();
	}

	protected File getJarDirectory(File parentDirectory, String type) {
		return new File(parentDirectory, getJarVersionString(type));
	}

	protected String getJarVersionString(String type) {
		return String.format("%s-%s-%s-%s%s", minecraftProvider.getMinecraftVersion(), type, getExtension().getMappingsProvider().mappingsName, getExtension().getMappingsProvider().mappingsVersion, minecraftProvider.getJarSuffix());
	}

	public File getIntermediaryJar() {
		return minecraftIntermediaryJar;
	}

	public File getSrgJar() {
		return minecraftSrgJar;
	}

	public File getMappedJar() {
		return minecraftMappedJar;
	}

	public File getUnpickedJar() {
		return new File(getJarDirectory(getExtension().getUserCache(), "mapped"), "minecraft-" + getJarVersionString("unpicked") + ".jar");
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT_NAMED;
	}
}
