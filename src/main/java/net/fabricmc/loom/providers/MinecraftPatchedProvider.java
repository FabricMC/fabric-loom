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

import com.google.common.collect.ImmutableMap;
import de.oceanlabs.mcp.mcinjector.adaptors.ParameterAnnotationFixer;
import net.fabricmc.loom.util.*;
import net.fabricmc.loom.util.function.FsPathConsumer;
import net.fabricmc.loom.util.srg.InnerClassRemapper;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.minecraftforge.accesstransformer.TransformerProcessor;
import net.minecraftforge.binarypatcher.ConsoleTool;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MinecraftPatchedProvider extends DependencyProvider {
	private File minecraftClientSrgJar;
	private File minecraftServerSrgJar;
	private File minecraftClientPatchedSrgJar;
	private File minecraftServerPatchedSrgJar;
	private File minecraftClientPatchedOfficialJar;
	private File minecraftServerPatchedOfficialJar;
	private File minecraftMergedPatchedJar;
	private File projectAtHash;
	@Nullable
	private File projectAt = null;
	private boolean atDirty = false;

	public MinecraftPatchedProvider(Project project) {
		super(project);
	}

	public void initFiles() throws IOException {
		projectAtHash = new File(getExtension().getProjectPersistentCache(), "at.sha256");

		SourceSet main = getProject().getConvention().findPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");

		for (File srcDir : main.getResources().getSrcDirs()) {
			File projectAt = new File(srcDir, "META-INF/accesstransformer.cfg");

			if (projectAt.exists()) {
				this.projectAt = projectAt;
				break;
			}
		}

		if (isRefreshDeps() || !projectAtHash.exists()) {
			writeAtHash();
			atDirty = projectAt != null;
		} else {
			byte[] expected = com.google.common.io.Files.asByteSource(projectAtHash).read();
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

		minecraftClientPatchedOfficialJar = new File(cache, "minecraft-" + minecraftVersion + "-client" + jarSuffix + ".jar");
		minecraftServerPatchedOfficialJar = new File(cache, "minecraft-" + minecraftVersion + "-server" + jarSuffix + ".jar");
		minecraftClientSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-client-srg.jar");
		minecraftServerSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-server-srg.jar");
		minecraftClientPatchedSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-client-srg" + jarSuffix + ".jar");
		minecraftServerPatchedSrgJar = new File(cache, "minecraft-" + minecraftVersion + "-server-srg" + jarSuffix + ".jar");
		minecraftMergedPatchedJar = new File(cache, "minecraft-" + minecraftVersion + "-merged" + jarSuffix + ".jar");

		if (isRefreshDeps()) {
			cleanCache();
		}

		if (!minecraftClientSrgJar.exists() || !minecraftServerSrgJar.exists()
		    || !minecraftClientPatchedSrgJar.exists() || !minecraftServerPatchedSrgJar.exists()
		    || !minecraftMergedPatchedJar.exists()) {
			minecraftClientSrgJar.delete();
			minecraftServerSrgJar.delete();
			minecraftClientPatchedSrgJar.delete();
			minecraftServerPatchedSrgJar.delete();
			minecraftMergedPatchedJar.delete();
		}
	}

	public void cleanCache() {
		for (File file : Arrays.asList(
				projectAtHash,
				minecraftClientSrgJar,
				minecraftServerSrgJar,
				minecraftClientPatchedSrgJar,
				minecraftServerPatchedSrgJar,
				minecraftClientPatchedOfficialJar,
				minecraftServerPatchedOfficialJar,
				minecraftMergedPatchedJar
		)) {
			file.delete();
		}
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		initFiles();

		if (atDirty) {
			getProject().getLogger().lifecycle(":found dirty access transformers");
		}

		if (atDirty || !minecraftClientPatchedOfficialJar.exists() || !minecraftServerPatchedOfficialJar.exists()) {
			if (!minecraftClientSrgJar.exists() || !minecraftServerSrgJar.exists()) {
				// Remap official jars to MCPConfig remapped srg jars
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
		logger.lifecycle(":remapping minecraft (MCPRuntime, official -> srg)");

		McpConfigProvider mcpProvider = getExtension().getMcpConfigProvider();
		File root = new File(getExtension().getUserCache(), "mcp_root");
		root.mkdirs();
		MCPWrapper wrapper = new MCPWrapper(mcpProvider.getMcp(), root);

		// Client
		{
			MCPRuntime runtime = wrapper.getRuntime(getProject(), "client");
			File output = runtime.execute(logger, "rename");
			FileUtils.copyFile(output, minecraftClientSrgJar);
		}

		// Server
		{
			MCPRuntime runtime = wrapper.getRuntime(getProject(), "server");
			File output = runtime.execute(logger, "rename");
			FileUtils.copyFile(output, minecraftServerSrgJar);
		}
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
		for (Environment environment : Environment.values()) {
			String side = environment.side();
			File target = environment.patchedSrgJar.apply(this);

			File atJar = File.createTempFile("at" + side, ".jar");
			File at = File.createTempFile("at" + side, ".cfg");
			FileUtils.copyFile(target, atJar);
			JarUtil.extractFile(atJar, "META-INF/accesstransformer.cfg", at);
			String[] args = new String[]{
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

	private enum Environment {
		CLIENT(provider -> provider.minecraftClientPatchedSrgJar, provider -> provider.minecraftClientPatchedOfficialJar),
		SERVER(provider -> provider.minecraftServerPatchedSrgJar, provider -> provider.minecraftServerPatchedOfficialJar);

		Function<MinecraftPatchedProvider, File> patchedSrgJar;
		Function<MinecraftPatchedProvider, File> patchedOfficialJar;

		Environment(Function<MinecraftPatchedProvider, File> patchedSrgJar, Function<MinecraftPatchedProvider, File> patchedOfficialJar) {
			this.patchedSrgJar = patchedSrgJar;
			this.patchedOfficialJar = patchedOfficialJar;
		}

		public String side() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private void remapPatchedJars(Logger logger) throws Exception {
		Path[] libraries = getExtension()
				.getMinecraftProvider()
				.getLibraryProvider()
				.getLibraries()
				.stream()
				.map(File::toPath)
				.toArray(Path[]::new);

		ExecutorService service = Executors.newFixedThreadPool(2);
		List<Future<?>> futures = new LinkedList<>();

		for (Environment environment : Environment.values()) {
			futures.add(service.submit(() -> {
				try {
					logger.lifecycle(":remapping minecraft (TinyRemapper, " + environment.side() + ", srg -> official)");
					TinyTree mappingsWithSrg = getExtension().getMappingsProvider().getMappingsWithSrg();

					Path input = environment.patchedSrgJar.apply(this).toPath();
					Path output = environment.patchedOfficialJar.apply(this).toPath();
					
					Files.deleteIfExists(output);
					
					TinyRemapper remapper = TinyRemapper.newRemapper()
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
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}));
		}

		for (Future<?> future : futures) {
			future.get();
		}
	}
	
	

	

	private void patchJars(Logger logger) throws Exception {
		logger.lifecycle(":patching jars");

		PatchProvider patchProvider = getExtension().getPatchProvider();
		patchJars(minecraftClientSrgJar, minecraftClientPatchedSrgJar, patchProvider.clientPatches);
		patchJars(minecraftServerSrgJar, minecraftServerPatchedSrgJar, patchProvider.serverPatches);

		logger.lifecycle(":copying missing classes into patched jars");
		copyMissingClasses(minecraftClientSrgJar, minecraftClientPatchedSrgJar);
		copyMissingClasses(minecraftServerSrgJar, minecraftServerPatchedSrgJar);

		logger.lifecycle(":fixing parameter annotations for patched jars");
		fixParameterAnnotation(minecraftClientPatchedSrgJar);
		fixParameterAnnotation(minecraftServerPatchedSrgJar);
	}

	private void patchJars(File clean, File output, Path patches) throws IOException {
		PrintStream previous = System.out;
		try {
			System.setOut(new PrintStream(new NullOutputStream()));
		} catch (SecurityException ignored) {
		}
		ConsoleTool.main(new String[]{
				"--clean", clean.getAbsolutePath(),
				"--output", output.getAbsolutePath(),
				"--apply", patches.toAbsolutePath().toString()
		});
		try {
			System.setOut(previous);
		} catch (SecurityException ignored) {
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
		try (FileSystem sourceFs = FileSystems.newFileSystem(new URI("jar:" + source.toURI()), ImmutableMap.of("create", false));
		     FileSystem targetFs = FileSystems.newFileSystem(new URI("jar:" + target.toURI()), ImmutableMap.of("create", false))) {
			for (Path sourceDir : toWalk.apply(sourceFs)) {
				Path dir = sourceDir.toAbsolutePath();
				Files.walk(dir)
						.filter(Files::isRegularFile)
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
