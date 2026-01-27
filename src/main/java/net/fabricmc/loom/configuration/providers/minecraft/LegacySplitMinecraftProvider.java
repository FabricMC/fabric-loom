package net.fabricmc.loom.configuration.providers.minecraft;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LegacySplitMinecraftProvider extends MinecraftProvider {
	private Path minecraftClientOnlyJar;
	private Path minecraftServerOnlyJar;
	private Path minecraftCommonJar;

	public LegacySplitMinecraftProvider(MinecraftMetadataProvider metadataProvider, ConfigContext configContext) {
		super(metadataProvider, configContext);
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		minecraftClientOnlyJar = path("minecraft-client-only.jar");
		minecraftServerOnlyJar = path("minecraft-server-only.jar");
		minecraftCommonJar = path("minecraft-common.jar");
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(minecraftClientOnlyJar, minecraftServerOnlyJar, minecraftCommonJar);
	}

	@Override
	public MappingsNamespace getOfficialNamespace() {
		return MappingsNamespace.OFFICIAL;
	}

	@Override
	public void provide() throws Exception {
		super.provide();

		boolean requiresRefresh = getExtension().refreshDeps() || Files.notExists(minecraftClientOnlyJar) || Files.notExists(minecraftServerOnlyJar) || Files.notExists(minecraftCommonJar);

		if (!requiresRefresh) {
			return;
		}

		if (!isLegacyVersion()) {
			throw new UnsupportedOperationException("Invalid version for legacy splitting! Version must be 1.2 or below.");
		}

		final Path clientJar = getMinecraftClientJar().toPath();
		final Path serverJar = getMinecraftServerJar().toPath();

		try (LegacyMinecraftJarSplitter jarSplitter = new LegacyMinecraftJarSplitter(clientJar, serverJar)) {
			// Required for loader to compute the version info also useful to have in both jars.
			jarSplitter.sharedEntry("version.json");
			jarSplitter.sharedEntry("assets/.mcassetsroot");
			jarSplitter.sharedEntry("assets/minecraft/lang/en_us.json");

			jarSplitter.split(minecraftClientOnlyJar, minecraftServerOnlyJar, minecraftCommonJar);
		} catch (Exception e) {
			Files.deleteIfExists(minecraftClientOnlyJar);
			Files.deleteIfExists(minecraftServerOnlyJar);
			Files.deleteIfExists(minecraftCommonJar);

			throw new RuntimeException("Failed to split minecraft", e);
		}
	}

	public Path getMinecraftClientOnlyJar() {
		return minecraftClientOnlyJar;
	}

	public Path getMinecraftServerOnlyJar() {
		return minecraftServerOnlyJar;
	}

	public Path getMinecraftCommonJar() {
		return minecraftCommonJar;
	}
}
