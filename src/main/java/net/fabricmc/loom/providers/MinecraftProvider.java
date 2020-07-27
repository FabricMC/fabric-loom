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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.zip.ZipError;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.binarypatcher.ConsoleTool;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.IoConsumer;
import net.fabricmc.loom.util.ManifestVersion;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.StaticPathWatcher;

public class MinecraftProvider extends DependencyProvider {
	private String minecraftVersion;

	private MinecraftVersionInfo versionInfo;
	private MinecraftLibraryProvider libraryProvider;

	private File minecraftJson;
	private File minecraftClientJar;
	private File minecraftServerJar;
	private File minecraftClientSrgJar;
	private File minecraftServerSrgJar;
	private File minecraftClientPatchedSrgJar;
	private File minecraftServerPatchedSrgJar;
	private File minecraftClientPatchedJar;
	private File minecraftServerPatchedJar;
	private File minecraftMergedJar;
	private File minecraftMergedSrgJar;

	Gson gson = new Gson();

	public MinecraftProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();

		boolean offline = getProject().getGradle().getStartParameter().isOffline();

		initFiles();

		downloadMcJson(offline);

		try (FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = gson.fromJson(reader, MinecraftVersionInfo.class);
		}

		// Add Loom as an annotation processor
		addDependency(getProject().files(this.getClass().getProtectionDomain().getCodeSource().getLocation()), "compileOnly");

		if (offline) {
			if (minecraftClientJar.exists() && minecraftServerJar.exists()) {
				getProject().getLogger().debug("Found client and server jars, presuming up-to-date");
			} else if (minecraftMergedJar.exists()) {
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				getProject().getLogger().warn("Missing game jar but merged jar present, things might end badly");
			} else {
				throw new GradleException("Missing jar(s); Client: " + minecraftClientJar.exists() + ", Server: " + minecraftServerJar.exists());
			}
		} else {
			downloadJars(getProject().getLogger());
		}

		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, getProject());

		if (!minecraftClientPatchedJar.exists() || !minecraftServerPatchedJar.exists()) {
			if (!minecraftClientSrgJar.exists() || !minecraftServerSrgJar.exists()) {
				createSrgJars(getProject().getLogger());
			}

			if (!minecraftClientPatchedSrgJar.exists() || !minecraftServerPatchedSrgJar.exists()) {
				patchJars(getProject().getLogger());
			}

			createPatchedJars(getProject().getLogger());
		}

		if (!minecraftMergedJar.exists() || !minecraftMergedSrgJar.exists() || isRefreshDeps()) {
			try {
				mergeJars(getProject().getLogger());
			} catch (ZipError e) {
				DownloadUtil.delete(minecraftClientJar);
				DownloadUtil.delete(minecraftServerJar);
				DownloadUtil.delete(minecraftClientPatchedJar);
				DownloadUtil.delete(minecraftServerPatchedJar);
				DownloadUtil.delete(minecraftClientSrgJar);
				DownloadUtil.delete(minecraftServerSrgJar);
				DownloadUtil.delete(minecraftClientPatchedSrgJar);
				DownloadUtil.delete(minecraftServerPatchedSrgJar);

				getProject().getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
				throw new RuntimeException();
			}
		}
	}

	private void initFiles() {
		minecraftJson = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-info.json");
		minecraftClientJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-client.jar");
		minecraftServerJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-server.jar");
		PatchProvider patchProvider = getExtension().getPatchProvider();
		minecraftClientPatchedJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-client-patched-" + patchProvider.forgeVersion + ".jar");
		minecraftServerPatchedJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-server-patched-" + patchProvider.forgeVersion + ".jar");
		minecraftClientSrgJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-client-srg.jar");
		minecraftServerSrgJar = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-server-srg.jar");
		minecraftClientPatchedSrgJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-client-patched-srg-" + patchProvider.forgeVersion + ".jar");
		minecraftServerPatchedSrgJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-server-patched-srg-" + patchProvider.forgeVersion + ".jar");
		minecraftMergedJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-merged-patched-" + patchProvider.forgeVersion + ".jar");
		minecraftMergedSrgJar = new File(getExtension().getProjectPersistentCache(), "minecraft-" + minecraftVersion + "-merged-patched-" + patchProvider.forgeVersion + "-srg.jar");
	}

	private void downloadMcJson(boolean offline) throws IOException {
		File manifests = new File(getExtension().getUserCache(), "version_manifest.json");

		if (getExtension().isShareCaches() && !getExtension().isRootProject() && manifests.exists() && !isRefreshDeps()) {
			return;
		}

		if (offline) {
			if (manifests.exists()) {
				// If there is the manifests already we'll presume that's good enough
				getProject().getLogger().debug("Found version manifests, presuming up-to-date");
			} else {
				// If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Version manifests not found at " + manifests.getAbsolutePath());
			}
		} else {
			getProject().getLogger().debug("Downloading version manifests");
			DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), manifests, getProject().getLogger());
		}

		String versionManifest = Files.asCharSource(manifests, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = new GsonBuilder().create().fromJson(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();

		if (getExtension().customManifest != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = getExtension().customManifest;
			optionalVersion = Optional.of(customVersion);
			getProject().getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (!optionalVersion.isPresent()) {
			optionalVersion = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
		}

		if (optionalVersion.isPresent()) {
			if (offline) {
				if (minecraftJson.exists()) {
					//If there is the manifest already we'll presume that's good enough
					getProject().getLogger().debug("Found Minecraft {} manifest, presuming up-to-date", minecraftVersion);
				} else {
					//If we don't have the manifests then there's nothing more we can do
					throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at " + minecraftJson.getAbsolutePath());
				}
			} else {
				if (StaticPathWatcher.INSTANCE.hasFileChanged(minecraftJson.toPath()) || isRefreshDeps()) {
					getProject().getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
					DownloadUtil.downloadIfChanged(new URL(optionalVersion.get().url), minecraftJson, getProject().getLogger());
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	private void downloadJars(Logger logger) throws IOException {
		if (getExtension().isShareCaches() && !getExtension().isRootProject() && minecraftClientJar.exists() && minecraftServerJar.exists() && !isRefreshDeps()) {
			return;
		}

		DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("client").url), minecraftClientJar, logger);
		DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("server").url), minecraftServerJar, logger);
	}

	private void createSrgJars(Logger logger) throws Exception {
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

	private void createPatchedJars(Logger logger) throws IOException {
		logger.lifecycle(":remapping minecraft (Atlas, srg -> official)");

		useAtlas(MappingSet::reverse, atlas -> {
			atlas.run(minecraftClientPatchedSrgJar.toPath(), minecraftClientPatchedJar.toPath());
			atlas.run(minecraftServerPatchedSrgJar.toPath(), minecraftServerPatchedJar.toPath());
		});
	}

	private void useAtlas(UnaryOperator<MappingSet> mappingOp, IoConsumer<Atlas> action) throws IOException {
		try (Reader mappingReader = new FileReader(getExtension().getMcpConfigProvider().getSrg());
				TSrgReader reader = new TSrgReader(mappingReader);
				Atlas atlas = new Atlas()) {
			MappingSet mappings = mappingOp.apply(reader.read());

			atlas.install(ctx -> new JarEntryRemappingTransformer(
					new LorenzRemapper(mappings, ctx.inheritanceProvider())
			));

			for (File library : getLibraryProvider().getLibraries()) {
				atlas.use(library.toPath());
			}

			action.accept(atlas);
		}
	}

	private void patchJars(Logger logger) throws IOException {
		logger.lifecycle(":patching jars");

		PatchProvider patchProvider = getExtension().getPatchProvider();
		patchJars(minecraftClientSrgJar, minecraftClientPatchedSrgJar, patchProvider.clientPatches);
		patchJars(minecraftServerSrgJar, minecraftServerPatchedSrgJar, patchProvider.serverPatches);
	}

	private void patchJars(File clean, File output, Path patches) throws IOException {
		ConsoleTool.main(new String[]{
				"--clean", clean.getAbsolutePath(),
				"--output", output.getAbsolutePath(),
				"--apply", patches.toAbsolutePath().toString()
		});
	}

	private void mergeJars(Logger logger) throws IOException {
		logger.lifecycle(":merging jars");

		// FIXME: Hack here: There are no server-only classes so we can just copy the client JAR.
		Files.copy(minecraftClientPatchedJar, minecraftMergedJar);
		Files.copy(minecraftClientPatchedSrgJar, minecraftMergedSrgJar);

		// Copy resources
		copyNonClassFiles(minecraftClientJar, minecraftMergedJar);
		copyNonClassFiles(minecraftClientJar, minecraftMergedSrgJar);
		copyNonClassFiles(minecraftServerJar, minecraftMergedJar);
		copyNonClassFiles(minecraftServerJar, minecraftMergedSrgJar);

		/*try (JarMerger jarMerger = new JarMerger(minecraftClientPatchedJar, minecraftServerPatchedJar, minecraftMergedJar)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}*/
	}

	private void copyNonClassFiles(File source, File target) throws IOException {
		try (FileSystem sourceFs = FileSystems.newFileSystem(new URI("jar:" + source.toURI()), ImmutableMap.of("create", false));
				FileSystem targetFs = FileSystems.newFileSystem(new URI("jar:" + source.toURI()), ImmutableMap.of("create", false))) {
			for (Path rootDirectory : sourceFs.getRootDirectories()) {
				java.nio.file.Files.walk(rootDirectory)
						.filter(java.nio.file.Files::isRegularFile)
						.filter(it -> !it.toString().endsWith(".class"))
						.forEach(it -> {
							try {
								java.nio.file.Files.copy(it, targetFs.getPath(it.toString()));
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
			}
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	public File getMergedJar() {
		return minecraftMergedJar;
	}

	public File getMergedSrgJar() {
		return minecraftMergedSrgJar;
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}

	public MinecraftVersionInfo getVersionInfo() {
		return versionInfo;
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return libraryProvider;
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT;
	}
}
