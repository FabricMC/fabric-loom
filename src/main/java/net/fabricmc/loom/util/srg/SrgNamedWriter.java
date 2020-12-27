package net.fabricmc.loom.util.srg;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyTree;
import org.cadixdev.lorenz.io.srg.SrgWriter;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SrgNamedWriter {
	public static void writeTo(Logger logger, Path srgFile, TinyTree mappings) throws IOException {
		Files.deleteIfExists(srgFile);
		try (SrgWriter writer = new SrgWriter(Files.newBufferedWriter(srgFile))) {
			try (TinyMappingsReader reader = new TinyMappingsReader(mappings, "srg", "named")) {
				writer.write(reader.read());
			}
		}
	}
}
