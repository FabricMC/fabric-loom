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

package net.fabricmc.loom.configuration.providers.forge;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;
import de.oceanlabs.mcp.mcinjector.adaptors.ParameterAnnotationFixer;
import net.minecraftforge.binarypatcher.ConsoleTool;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.accesstransformer.AccessTransformerJarProcessor;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ThreadingUtils;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.loom.util.function.FsPathConsumer;
import net.fabricmc.loom.util.srg.InnerClassRemapper;
import net.fabricmc.loom.util.srg.SpecialSourceExecutor;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class MinecraftPatchedProvider extends DependencyProvider {
	private final MappingsProvider mappingsProvider;
	// Step 1: Remap Minecraft to SRG
	private File minecraftClientSrgJar;
	private File minecraftServerSrgJar;
	// Step 2: Binary Patch
	private File minecraftClientPatchedSrgJar;
	private File minecraftServerPatchedSrgJar;
	// Step 3: Remap Patched Jar to Official
	private File minecraftClientPatchedOfficialJar;
	private File minecraftServerPatchedOfficialJar;
	// Step 4: Merge
	private File minecraftMergedPatchedJar;

	public MinecraftPatchedProvider(MappingsProvider mappingsProvider, Project project) {
		super(project);
		this.mappingsProvider = mappingsProvider;
	}

	public void initFiles() throws IOException {
		MinecraftProvider minecraftProvider = getExtension().getMinecraftProvider();
		PatchProvider patchProvider = getExtension().getPatchProvider();
		String minecraftVersion = minecraftProvider.getMinecraftVersion();
		String jarSuffix = "-patched-forge-" + patchProvider.forgeVersion;

		if (getExtension().useFabricMixin) {
			jarSuffix += "-fabric-mixin";
		}

		minecraftProvider.setJarSuffix(jarSuffix);

		File globalCache = getExtension().getUserCache();

		minecraftClientSrgJar = new File(globalCache, "minecraft-" + minecraftVersion + "-client-srg.jar");
		minecraftServerSrgJar = new File(globalCache, "minecraft-" + minecraftVersion + "-server-srg.jar");
		minecraftClientPatchedSrgJar = new File(globalCache, "minecraft-" + minecraftVersion + "-client-srg-patched" + jarSuffix + ".jar");
		minecraftServerPatchedSrgJar = new File(globalCache, "minecraft-" + minecraftVersion + "-server-srg-patched" + jarSuffix + ".jar");
		minecraftClientPatchedOfficialJar = new File(globalCache, "minecraft-" + minecraftVersion + "-client-patched" + jarSuffix + ".jar");
		minecraftServerPatchedOfficialJar = new File(globalCache, "minecraft-" + minecraftVersion + "-server-patched" + jarSuffix + ".jar");
		minecraftMergedPatchedJar = new File(globalCache, "minecraft-" + minecraftVersion + "-merged-patched" + jarSuffix + ".jar");

		if (isRefreshDeps() || Stream.of(getGlobalCaches()).anyMatch(Predicates.not(File::exists))) {
			cleanAllCache();
		} else if (Stream.of(getProjectCache()).anyMatch(Predicates.not(File::exists))) {
			cleanProjectCache();
		}
	}

	public void cleanAllCache() {
		for (File file : getGlobalCaches()) {
			file.delete();
		}

		cleanProjectCache();
	}

	private File[] getGlobalCaches() {
		return new File[] {
				minecraftClientSrgJar,
				minecraftServerSrgJar,
				minecraftClientPatchedSrgJar,
				minecraftServerPatchedSrgJar
		};
	}

	public void cleanProjectCache() {
		for (File file : getProjectCache()) {
			file.delete();
		}
	}

	private File[] getProjectCache() {
		return new File[] {
				minecraftClientPatchedOfficialJar,
				minecraftServerPatchedOfficialJar,
				minecraftMergedPatchedJar
		};
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		initFiles();

		boolean dirty = false;

		if (!minecraftClientSrgJar.exists() || !minecraftServerSrgJar.exists()) {
			dirty = true;
			// Remap official jars to MCPConfig remapped srg jars
			createSrgJars(getProject().getLogger());
		}

		if (!minecraftClientPatchedSrgJar.exists() || !minecraftServerPatchedSrgJar.exists()) {
			dirty = true;
			patchJars(getProject().getLogger());
			injectForgeClasses(getProject().getLogger());
			accessTransformForge(getProject().getLogger());
		}

		if (dirty) {
			remapPatchedJars(getProject().getLogger());
		}

		if (dirty || !minecraftMergedPatchedJar.exists()) {
			mergeJars(getProject().getLogger());
		}
	}

	private void createSrgJars(Logger logger) throws Exception {
		McpConfigProvider mcpProvider = getExtension().getMcpConfigProvider();

		MinecraftProvider minecraftProvider = getExtension().getMinecraftProvider();

		String[] mappingsPath = {null};

		if (!ZipUtil.handle(mcpProvider.getMcp(), "config.json", (in, zipEntry) -> {
			mappingsPath[0] = new JsonParser().parse(new InputStreamReader(in)).getAsJsonObject().get("data").getAsJsonObject().get("mappings").getAsString();
		})) {
			throw new IllegalStateException("Failed to find 'config.json' in " + mcpProvider.getMcp().getAbsolutePath() + "!");
		}

		Path[] tmpSrg = {null};

		if (!ZipUtil.handle(mcpProvider.getMcp(), mappingsPath[0], (in, zipEntry) -> {
			tmpSrg[0] = Files.createTempFile(null, null);

			try (BufferedWriter writer = Files.newBufferedWriter(tmpSrg[0])) {
				IOUtils.copy(in, writer, StandardCharsets.UTF_8);
			}
		})) {
			throw new IllegalStateException("Failed to find mappings '" + mappingsPath[0] + "' in " + mcpProvider.getMcp().getAbsolutePath() + "!");
		}

		Dependency dependency = getProject().getDependencies().create("net.md-5:SpecialSource:1.8.3:shaded");
		Set<File> specialSourceJars = getProject().getConfigurations().detachedConfiguration(dependency).resolve();
		if (specialSourceJars.isEmpty()) throw new IllegalStateException("Failed to resolve net.md-5:SpecialSource:1.8.3:shaded");
		File specialSourceJar = specialSourceJars.iterator().next();

		ThreadingUtils.run(() -> {
			Files.copy(SpecialSourceExecutor.produceSrgJar(getProject(), mappingsProvider, "client", specialSourceJar, minecraftProvider.minecraftClientJar.toPath(), tmpSrg[0]), minecraftClientSrgJar.toPath());
		}, () -> {
				Files.copy(SpecialSourceExecutor.produceSrgJar(getProject(), mappingsProvider, "server", specialSourceJar, minecraftProvider.minecraftServerJar.toPath(), tmpSrg[0]), minecraftServerSrgJar.toPath());
			});
	}

	private void fixParameterAnnotation(File jarFile) throws Exception {
		getProject().getLogger().info(":fixing parameter annotations for " + jarFile.toString());

		try (FileSystem fs = FileSystems.newFileSystem(new URI("jar:" + jarFile.toURI()), ImmutableMap.of("create", false))) {
			for (Path rootDir : fs.getRootDirectories()) {
				for (Path file : (Iterable<? extends Path>) Files.walk(rootDir)::iterator) {
					if (!file.toString().endsWith(".class")) continue;
					byte[] bytes = Files.readAllBytes(file);
					ClassReader reader = new ClassReader(bytes);
					ClassNode node = new ClassNode();
					ClassVisitor visitor = new ParameterAnnotationFixer(node, null);
					reader.accept(visitor, 0);

					ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					node.accept(writer);
					byte[] out = writer.toByteArray();

					if (!Arrays.equals(bytes, out)) {
						Files.delete(file);
						Files.write(file, out);
					}
				}
			}
		}
	}

	private void injectForgeClasses(Logger logger) throws IOException {
		logger.lifecycle(":injecting forge classes into minecraft");
		ThreadingUtils.run(Arrays.asList(Environment.values()), environment -> {
			copyAll(getExtension().getForgeUniversalProvider().getForge(), environment.patchedSrgJar.apply(this));
			copyUserdevFiles(getExtension().getForgeUserdevProvider().getUserdevJar(), environment.patchedSrgJar.apply(this));
		});

		logger.lifecycle(":injecting loom classes into minecraft");
		File injection = File.createTempFile("loom-injection", ".jar");

		try (InputStream in = MinecraftProvider.class.getResourceAsStream("/inject/injection.jar")) {
			FileUtils.copyInputStreamToFile(in, injection);
		}

		for (Environment environment : Environment.values()) {
			String side = environment.side();
			File target = environment.patchedSrgJar.apply(this);
			walkFileSystems(injection, target, it -> {
				if (it.getFileName().toString().equals("MANIFEST.MF")) {
					return false;
				}

				return getExtension().useFabricMixin || !it.getFileName().toString().endsWith("cpw.mods.modlauncher.api.ITransformationService");
			}, this::copyReplacing);
		}
	}

	private void accessTransformForge(Logger logger) throws Exception {
		for (Environment environment : Environment.values()) {
			String side = environment.side();
			logger.lifecycle(":access transforming minecraft (" + side + ")");

			File file = environment.patchedSrgJar.apply(this);
			AccessTransformerJarProcessor.accessTransformForge(file, file, true);
		}
	}

	private enum Environment {
		CLIENT(provider -> provider.minecraftClientSrgJar,
				provider -> provider.minecraftClientPatchedSrgJar,
				provider -> provider.minecraftClientPatchedOfficialJar
		),
		SERVER(provider -> provider.minecraftServerSrgJar,
				provider -> provider.minecraftServerPatchedSrgJar,
				provider -> provider.minecraftServerPatchedOfficialJar
		);

		final Function<MinecraftPatchedProvider, File> srgJar;
		final Function<MinecraftPatchedProvider, File> patchedSrgJar;
		final Function<MinecraftPatchedProvider, File> patchedOfficialJar;

		Environment(Function<MinecraftPatchedProvider, File> srgJar,
				Function<MinecraftPatchedProvider, File> patchedSrgJar,
				Function<MinecraftPatchedProvider, File> patchedOfficialJar) {
			this.srgJar = srgJar;
			this.patchedSrgJar = patchedSrgJar;
			this.patchedOfficialJar = patchedOfficialJar;
		}

		public String side() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private void remapPatchedJars(Logger logger) throws Exception {
		Path[] libraries = MinecraftMappedProvider.getRemapClasspath(getProject());

		ThreadingUtils.run(Arrays.asList(Environment.values()), environment -> {
			logger.lifecycle(":remapping minecraft (TinyRemapper, " + environment.side() + ", srg -> official)");
			TinyTree mappingsWithSrg = getExtension().getMappingsProvider().getMappingsWithSrg();

			Path input = environment.patchedSrgJar.apply(this).toPath();
			Path output = environment.patchedOfficialJar.apply(this).toPath();

			Files.deleteIfExists(output);

			TinyRemapper remapper = TinyRemapper.newRemapper()
					.logger(getProject().getLogger()::lifecycle)
					.withMappings(TinyRemapperMappingsHelper.create(mappingsWithSrg, "srg", "official", true))
					.withMappings(InnerClassRemapper.of(input, mappingsWithSrg, "srg", "official"))
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.fixPackageAccess(true)
					.build();

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
				outputConsumer.addNonClassFiles(input);

				remapper.readClassPath(libraries);
				remapper.readInputs(input);
				remapper.apply(outputConsumer);
			} finally {
				remapper.finish();
			}
		});
	}

	private void patchJars(Logger logger) throws IOException {
		logger.lifecycle(":patching jars");

		PatchProvider patchProvider = getExtension().getPatchProvider();
		patchJars(minecraftClientSrgJar, minecraftClientPatchedSrgJar, patchProvider.clientPatches);
		patchJars(minecraftServerSrgJar, minecraftServerPatchedSrgJar, patchProvider.serverPatches);

		ThreadingUtils.run(Arrays.asList(Environment.values()), environment -> {
			copyMissingClasses(environment.srgJar.apply(this), environment.patchedSrgJar.apply(this));
			fixParameterAnnotation(environment.patchedSrgJar.apply(this));
		});
	}

	private void patchJars(File clean, File output, Path patches) throws IOException {
		PrintStream previous = System.out;

		try {
			System.setOut(new PrintStream(new NullOutputStream()));
		} catch (SecurityException ignored) {
			// Failed to replace logger filter, just ignore
		}

		ConsoleTool.main(new String[] {
				"--clean", clean.getAbsolutePath(),
				"--output", output.getAbsolutePath(),
				"--apply", patches.toAbsolutePath().toString()
		});

		try {
			System.setOut(previous);
		} catch (SecurityException ignored) {
			// Failed to replace logger filter, just ignore
		}
	}

	private void mergeJars(Logger logger) throws IOException {
		// FIXME: Hack here: There are no server-only classes so we can just copy the client JAR.
		FileUtils.copyFile(minecraftClientPatchedOfficialJar, minecraftMergedPatchedJar);

		logger.lifecycle(":copying resources");

		// Copy resources
		MinecraftProvider minecraftProvider = getExtension().getMinecraftProvider();
		copyNonClassFiles(minecraftProvider.minecraftClientJar, minecraftMergedPatchedJar);
		copyNonClassFiles(minecraftProvider.minecraftServerJar, minecraftMergedPatchedJar);
	}

	private void walkFileSystems(File source, File target, Predicate<Path> filter, Function<FileSystem, Iterable<Path>> toWalk, FsPathConsumer action)
			throws IOException {
		try (FileSystemUtil.FileSystemDelegate sourceFs = FileSystemUtil.getJarFileSystem(source, false);
				FileSystemUtil.FileSystemDelegate targetFs = FileSystemUtil.getJarFileSystem(target, false)) {
			for (Path sourceDir : toWalk.apply(sourceFs.get())) {
				Path dir = sourceDir.toAbsolutePath();
				Files.walk(dir)
						.filter(Files::isRegularFile)
						.filter(filter)
						.forEach(it -> {
							boolean root = dir.getParent() == null;

							try {
								Path relativeSource = root ? it : dir.relativize(it);
								Path targetPath = targetFs.get().getPath(relativeSource.toString());
								action.accept(sourceFs.get(), targetFs.get(), it, targetPath);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
			}
		}
	}

	private void walkFileSystems(File source, File target, Predicate<Path> filter, FsPathConsumer action) throws IOException {
		walkFileSystems(source, target, filter, FileSystem::getRootDirectories, action);
	}

	private void copyAll(File source, File target) throws IOException {
		walkFileSystems(source, target, it -> true, this::copyReplacing);
	}

	private void copyMissingClasses(File source, File target) throws IOException {
		walkFileSystems(source, target, it -> it.toString().endsWith(".class"), (sourceFs, targetFs, sourcePath, targetPath) -> {
			if (Files.exists(targetPath)) return;
			Path parent = targetPath.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.copy(sourcePath, targetPath);
		});
	}

	private void copyNonClassFiles(File source, File target) throws IOException {
		walkFileSystems(source, target, it -> !it.toString().endsWith(".class"), this::copyReplacing);
	}

	private void copyReplacing(FileSystem sourceFs, FileSystem targetFs, Path sourcePath, Path targetPath) throws IOException {
		Path parent = targetPath.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void copyUserdevFiles(File source, File target) throws IOException {
		walkFileSystems(source, target, file -> true, fs -> Collections.singleton(fs.getPath("inject")), (sourceFs, targetFs, sourcePath, targetPath) -> {
			Path parent = targetPath.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.copy(sourcePath, targetPath);
		});
	}

	public File getMergedJar() {
		return minecraftMergedPatchedJar;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT;
	}
}
