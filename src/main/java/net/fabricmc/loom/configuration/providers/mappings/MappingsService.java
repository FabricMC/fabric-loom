package net.fabricmc.loom.configuration.providers.mappings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MappingsService implements SharedService {
	private final MemoryMappingTree mappingTree;

	public MappingsService(Path tinyMappings) {
		try {
			this.mappingTree = new MemoryMappingTree();
			MappingReader.read(tinyMappings, mappingTree);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read mappings", e);
		}
	}

	public static synchronized MappingsService create(SharedServiceManager serviceManager, Path tinyMappings) {
		return serviceManager.getOrCreateService("MappingsService:" + tinyMappings.toAbsolutePath(), () -> new MappingsService(tinyMappings));
	}

	public MemoryMappingTree getMappingTree() {
		return mappingTree;
	}
}
