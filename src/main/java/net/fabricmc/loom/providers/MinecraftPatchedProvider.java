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

package net.fabricmc.loom.providers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import net.minecraftforge.accesstransformer.TransformerProcessor;
import net.minecraftforge.binarypatcher.ConsoleTool;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.JarUtil;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.loom.util.function.FsPathConsumer;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class MinecraftPatchedProvider extends DependencyProvider {
	private File minecraftClientSrgJar;
	private File minecraftServerSrgJar;
	private File minecraftClientPatchedSrgJar;
	private File minecraftServerPatchedSrgJar;
	private File minecraftClientPatchedJar;
	private File minecraftServerPatchedJar;
	private File minecraftMergedPatchedJar;
	private File projectAtHash;
	@Nullable
	private File projectAt = null;
	private boolean atDirty = false;

	public MinecraftPatchedProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		initFiles();

		if (atDirty || !minecraftClientPatchedJar.exists() || !minecraftServerPatchedJar.exists()) {
			if (!minecraftClientSrgJar.exists() || !minecraftServerSrgJar.exists()) {
				createSrgJars(getProject().getLogger());
			}

			if (atDirty || !minecraftClientPatchedSrgJar.exists() || !minecraftServerPatchedSrgJar.exists()) {
				patchJars(getProject().getLogger());
				injectForgeClasses(getProject().getLogger());
			}

			remapPatchedJars(getProject().getLogger());
		}

		if (atDirty || !minecraftMergedPatchedJar.exists()) {
			mergeJars(getProject().getLogger());
		}
	}

	private void initFiles() throws IOException {
		projectAtHash = new File(getExtension().getProjectPersistentCache(), "at.sha256");

		SourceSet main = getProject().getConvention().findPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");

		for (File srcDir : main.getResources().getSrcDirs()) {
			File projectAt = new File(srcDir, "META-INF/accesstransformer.cfg");

			if (projectAt.exists()) {
				this.projectAt = projectAt;
				break;
			}
		}

		if (!projectAtHash.exists()) {
			writeAtHash();
			atDirty = projectAt != null;
		} else {
			byte[] expected = Files.asByteSource(projectAtHash).read();
			byte[] current = projectAt != null ? Checksum.sha256(projectAt) : Checksum.sha256("");
			boolean mismatched = !Arrays.equals(current, expected);

			if (mismatched) {
				writeAtHash();
			}

			atDirty = mismatched && projectAt != null;
		}

		MinecraftProvider minecraftProvider = getExtension().getMinecraftProvider();
		PatchProvider patchProvider = getExtension().getPatchProvider();
		String minecraftVersion = minecraftProvider.getMinecraftVersion();
		String jarSuffix = "-patched-forge-" + patchProvider.forgeVersion;
		minecraftProvider.setJarSuffix(jarSuffix);

		File cache = usesProjectCache() ? getExtension().getProjectPersistentCache() : getExtension().getUserCache();

		minecraftClientPatchedJar = new File(cache, "minecraft-" + minecraftVersion + "-client" + jarSuffix + ".jar");
		minecraftServerPatchedJar = new File(cache, "minecraft-" + minecraftVersion + "-server" + jarSuffix + ".jar");
		minecraftClientSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-client-srg.jar");
		minecraftServerSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-server-srg.jar");
		minecraftClientPatchedSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-client-srg" + jarSuffix + ".jar");
		minecraftServerPatchedSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-server-srg" + jarSuffix + ".jar");
		minecraftMergedPatchedJar = new File(cache, "minecraft-" + minecraftVersion + "-merged" + jarSuffix + ".jar");
	}

	private void writeAtHash() throws IOException {
		try (FileOutputStream out = new FileOutputStream(projectAtHash)) {
			if (projectAt != null) {
				out.write(Checksum.sha256(projectAt));
			} else {
				out.write(Checksum.sha256(""));
			}
		}
	}

	private void createSrgJars(Logger logger) throws Exception {
		// TODO: FORGE: Get rid of *this*
		logger.lifecycle(":remapping minecraft (MCP, official -> srg)");

		McpConfigProvider volde = getExtension().getMcpConfigProvider();
		File root = new File(getExtension().getUserCache(), "mcp_root");
		root.mkdirs();
		MCPWrapper wrapper = new MCPWrapper(volde.getMcp(), root);

		// Client
		{
			MCPRuntime runtime = wrapper.getRuntime(getProject(), "client");
			File output = runtime.execute(logger, "rename");
			Files.copy(output, minecraftClientSrgJar);
		}

		// Server
		{
			MCPRuntime runtime = wrapper.getRuntime(getProject(), "server");
			File output = runtime.execute(logger, "rename");
			Files.copy(output, minecraftServerSrgJar);
		}
	}

	private void injectForgeClasses(Logger logger) throws IOException {
		logger.lifecycle(":injecting forge classes into minecraft");
		copyAll(getExtension().getForgeUniversalProvider().getForge(), minecraftClientPatchedSrgJar);
		copyAll(getExtension().getForgeUniversalProvider().getForge(), minecraftServerPatchedSrgJar);

		copyUserdevFiles(getExtension().getForgeUserdevProvider().getUserdevJar(), minecraftClientPatchedSrgJar);
		copyUserdevFiles(getExtension().getForgeUserdevProvider().getUserdevJar(), minecraftServerPatchedSrgJar);

		logger.lifecycle(":injecting loom classes into minecraft");
		File injection = File.createTempFile("loom-injection", ".jar");

		try (InputStream in = MinecraftProvider.class.getResourceAsStream("/inject/injection.jar")) {
			FileUtils.copyInputStreamToFile(in, injection);
		}

		walkFileSystems(injection, minecraftClientPatchedSrgJar, it -> !it.getFileName().toString().equals("MANIFEST.MF"), this::copyReplacing);
		walkFileSystems(injection, minecraftServerPatchedSrgJar, it -> !it.getFileName().toString().equals("MANIFEST.MF"), this::copyReplacing);

		logger.lifecycle(":access transforming minecraft");

		boolean[] bools = { true, false };

		for (boolean isClient : bools) {
			String side = isClient ? "client" : "server";
			File target = isClient ? minecraftClientPatchedSrgJar : minecraftServerPatchedSrgJar;

			File atJar = File.createTempFile("at" + side, ".jar");
			File at = File.createTempFile("at" + side, ".cfg");
			Files.copy(target, atJar);
			JarUtil.extractFile(atJar, "META-INF/accesstransformer.cfg", at);
			String[] args = new String[] {
					"--inJar", atJar.getAbsolutePath(),
					"--outJar", target.getAbsolutePath(),
					"--atFile", at.getAbsolutePath()
			};

			if (projectAt != null) {
				args = Arrays.copyOf(args, args.length + 2);
				args[args.length - 2] = "--atFile";
				args[args.length - 1] = projectAt.getAbsolutePath();
			}

			TransformerProcessor.main(args);
		}
	}

	private void remapPatchedJars(Logger logger) throws IOException {
		boolean[] bools = { true, false };
		Path[] libraries = getExtension()
				.getMinecraftProvider()
				.getLibraryProvider()
				.getLibraries()
				.stream()
				.map(File::toPath)
				.toArray(Path[]::new);

		for (boolean isClient : bools) {
			logger.lifecycle(":remapping minecraft (TinyRemapper, " + (isClient ? "client" : "server") + ", srg -> official)");

			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(TinyRemapperMappingsHelper.create(getExtension().getMappingsProvider().getMappingsWithSrg(), "srg", "official", true))
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.build();

			Path input = (isClient ? minecraftClientPatchedSrgJar : minecraftServerPatchedSrgJar).toPath();
			Path output = (isClient ? minecraftClientPatchedJar : minecraftServerPatchedJar).toPath();

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
				outputConsumer.addNonClassFiles(input);

				remapper.readClassPath(libraries);
				remapper.readInputs(input);
				remapper.apply(outputConsumer);
			} finally {
				remapper.finish();
			}
		}
	}

	private void patchJars(Logger logger) throws IOException {
		logger.lifecycle(":patching jars");

		PatchProvider patchProvider = getExtension().getPatchProvider();
		patchJars(minecraftClientSrgJar, minecraftClientPatchedSrgJar, patchProvider.clientPatches);
		patchJars(minecraftServerSrgJar, minecraftServerPatchedSrgJar, patchProvider.serverPatches);

		logger.lifecycle(":copying missing classes into patched jars");
		copyMissingClasses(minecraftClientSrgJar, minecraftClientPatchedSrgJar);
		copyMissingClasses(minecraftServerSrgJar, minecraftServerPatchedSrgJar);
	}

	private void patchJars(File clean, File output, Path patches) throws IOException {
		ConsoleTool.main(new String[]{
				"--clean", clean.getAbsolutePath(),
				"--output", output.getAbsolutePath(),
				"--apply", patches.toAbsolutePath().toString()
		});
	}

	private void mergeJars(Logger logger) throws IOException {
		// FIXME: Hack here: There are no server-only classes so we can just copy the client JAR.
		Files.copy(minecraftClientPatchedJar, minecraftMergedPatchedJar);

		logger.lifecycle(":copying resources");

		// Copy resources
		MinecraftProvider minecraftProvider = getExtension().getMinecraftProvider();
		copyNonClassFiles(minecraftProvider.minecraftClientJar, minecraftMergedPatchedJar);
		copyNonClassFiles(minecraftProvider.minecraftServerJar, minecraftMergedPatchedJar);
	}

	private void walkFileSystems(File source, File target, Predicate<Path> filter, Function<FileSystem, Iterable<Path>> toWalk, FsPathConsumer action) throws IOException {
		try (FileSystem sourceFs = FileSystems.newFileSystem(new URI("jar:" + source.toURI()), ImmutableMap.of("create", false));
				FileSystem targetFs = FileSystems.newFileSystem(new URI("jar:" + target.toURI()), ImmutableMap.of("create", false))) {
			for (Path sourceDir : toWalk.apply(sourceFs)) {
				Path dir = sourceDir.toAbsolutePath();
				java.nio.file.Files.walk(dir)
						.filter(java.nio.file.Files::isRegularFile)
						.filter(filter)
						.forEach(it -> {
							boolean root = dir.getParent() == null;

							try {
								Path relativeSource = root ? it : dir.relativize(it);
								Path targetPath = targetFs.getPath(relativeSource.toString());
								action.accept(sourceFs, targetFs, it, targetPath);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
			}
		} catch (URISyntaxException e) {
			throw new IOException(e);
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
			if (java.nio.file.Files.exists(targetPath)) return;
			Path parent = targetPath.getParent();

			if (parent != null) {
				java.nio.file.Files.createDirectories(parent);
			}

			java.nio.file.Files.copy(sourcePath, targetPath);
		});
	}

	private void copyNonClassFiles(File source, File target) throws IOException {
		walkFileSystems(source, target, it -> !it.toString().endsWith(".class"), this::copyReplacing);
	}

	private void copyReplacing(FileSystem sourceFs, FileSystem targetFs, Path sourcePath, Path targetPath) throws IOException {
		Path parent = targetPath.getParent();

		if (parent != null) {
			java.nio.file.Files.createDirectories(parent);
		}

		java.nio.file.Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void copyUserdevFiles(File source, File target) throws IOException {
		walkFileSystems(source, target, file -> true, fs -> Collections.singleton(fs.getPath("inject")), (sourceFs, targetFs, sourcePath, targetPath) -> {
			Path parent = targetPath.getParent();

			if (parent != null) {
				java.nio.file.Files.createDirectories(parent);
			}

			java.nio.file.Files.copy(sourcePath, targetPath);
		});
	}

	public File getMergedJar() {
		return minecraftMergedPatchedJar;
	}

	public boolean usesProjectCache() {
		return projectAt != null;
	}

	public boolean isAtDirty() {
		return atDirty;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT;
	}
}
