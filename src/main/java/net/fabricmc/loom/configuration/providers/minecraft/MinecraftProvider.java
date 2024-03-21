/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.download.DownloadExecutor;
import net.fabricmc.loom.util.download.GradleDownloadProgressListener;
import net.fabricmc.loom.util.gradle.ProgressGroup;

public abstract class MinecraftProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftProvider.class);

	private final MinecraftMetadataProvider metadataProvider;

	private File minecraftClientJar;
	// Note this will be the boostrap jar starting with 21w39a
	private File minecraftServerJar;
	// The extracted server jar from the boostrap, only exists in >=21w39a
	private File minecraftExtractedServerJar;
	@Nullable
	private BundleMetadata serverBundleMetadata;

	private final ConfigContext configContext;

	public MinecraftProvider(MinecraftMetadataProvider metadataProvider, ConfigContext configContext) {
		this.metadataProvider = metadataProvider;
		this.configContext = configContext;
	}

	protected boolean provideClient() {
		return true;
	}

	protected boolean provideServer() {
		return true;
	}

	public void provide() throws Exception {
		initFiles();

		final MinecraftVersionMeta.JavaVersion javaVersion = getVersionInfo().javaVersion();

		if (javaVersion != null) {
			final int requiredMajorJavaVersion = getVersionInfo().javaVersion().majorVersion();
			final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

			if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
				throw new IllegalStateException("Minecraft " + minecraftVersion() + " requires Java " + requiredJavaVersion + " but Gradle is using " + JavaVersion.current());
			}
		}

		downloadJars();

		if (provideServer()) {
			serverBundleMetadata = BundleMetadata.fromJar(minecraftServerJar.toPath());
		}

		final MinecraftLibraryProvider libraryProvider = new MinecraftLibraryProvider(this, configContext.project());
		libraryProvider.provide();
	}

	protected void initFiles() {
		if (provideClient()) {
			minecraftClientJar = file("minecraft-client.jar");
		}

		if (provideServer()) {
			minecraftServerJar = file("minecraft-server.jar");
			minecraftExtractedServerJar = file("minecraft-extracted_server.jar");
		}
	}

	private void downloadJars() throws IOException {
		try (ProgressGroup progressGroup = new ProgressGroup(getProject(), "Download Minecraft jars");
				DownloadExecutor executor = new DownloadExecutor(2)) {
			if (provideClient()) {
				final MinecraftVersionMeta.Download client = getVersionInfo().download("client");
				getExtension().download(client.url())
						.sha1(client.sha1())
						.progress(new GradleDownloadProgressListener("Minecraft client", progressGroup::createProgressLogger))
						.downloadPathAsync(minecraftClientJar.toPath(), executor);
			}

			if (provideServer()) {
				final MinecraftVersionMeta.Download server = getVersionInfo().download("server");
				getExtension().download(server.url())
						.sha1(server.sha1())
						.progress(new GradleDownloadProgressListener("Minecraft server", progressGroup::createProgressLogger))
						.downloadPathAsync(minecraftServerJar.toPath(), executor);
			}
		}
	}

	protected final void extractBundledServerJar() throws IOException {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
		Objects.requireNonNull(getServerBundleMetadata(), "Cannot bundled mc jar from none bundled server jar");

		LOGGER.info(":Extracting server jar from bootstrap");

		if (getServerBundleMetadata().versions().size() != 1) {
			throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(getServerBundleMetadata().versions().size()));
		}

		getServerBundleMetadata().versions().get(0).unpackEntry(minecraftServerJar.toPath(), getMinecraftExtractedServerJar().toPath(), configContext.project());
	}

	public File workingDir() {
		return minecraftWorkingDirectory(configContext.project(), minecraftVersion());
	}

	public File dir(String path) {
		File dir = file(path);
		dir.mkdirs();
		return dir;
	}

	public File file(String path) {
		return new File(workingDir(), path);
	}

	public Path path(String path) {
		return file(path).toPath();
	}

	public File getMinecraftClientJar() {
		Preconditions.checkArgument(provideClient(), "Not configured to provide client jar");
		return minecraftClientJar;
	}

	// May be null on older versions
	@Nullable
	public File getMinecraftExtractedServerJar() {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
		return minecraftExtractedServerJar;
	}

	// This may be the server bundler jar on newer versions prob not what you want.
	public File getMinecraftServerJar() {
		Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
		return minecraftServerJar;
	}

	public String minecraftVersion() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getMinecraftVersion();
	}

	public MinecraftVersionMeta getVersionInfo() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getVersionMeta();
	}

	/**
	 * @return true if the minecraft version is older than 1.3.
	 */
	public boolean isLegacyVersion() {
		return !getVersionInfo().isVersionOrNewer(Constants.RELEASE_TIME_1_3);
	}

	@Nullable
	public BundleMetadata getServerBundleMetadata() {
		return serverBundleMetadata;
	}

	public abstract List<Path> getMinecraftJars();

	public abstract MappingsNamespace getOfficialNamespace();

	protected Project getProject() {
		return configContext.project();
	}

	protected LoomGradleExtension getExtension() {
		return configContext.extension();
	}

	public boolean refreshDeps() {
		return getExtension().refreshDeps();
	}

	public static File minecraftWorkingDirectory(Project project, String version) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		File workingDir = new File(extension.getFiles().getUserCache(), version);
		workingDir.mkdirs();
		return workingDir;
	}
}
