package net.fabricmc.loom.util.srg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cadixdev.lorenz.io.srg.SrgWriter;
import org.gradle.api.logging.Logger;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyTree;

public class SrgNamedWriter {
	public static void writeTo(Logger logger, Path srgFile, TinyTree mappings, String from, String to) throws IOException {
		Files.deleteIfExists(srgFile);

		try (SrgWriter writer = new SrgWriter(Files.newBufferedWriter(srgFile))) {
			try (TinyMappingsReader reader = new TinyMappingsReader(mappings, from, to)) {
				writer.write(reader.read());
			}
		}
	}
}
