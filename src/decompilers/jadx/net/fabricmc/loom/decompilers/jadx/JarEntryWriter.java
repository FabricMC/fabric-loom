package net.fabricmc.loom.decompilers.jadx;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class JarEntryWriter {
	private final Set<String> addedDirectories = new HashSet<>();
	private final JarOutputStream outputStream;

	JarEntryWriter(JarOutputStream outputStream) {
		this.outputStream = outputStream;
	}
	
	synchronized void write(String filename, byte[] data) throws IOException {
		String[] path = filename.split("/");
		String pathPart = "";

		for (int i = 0; i < path.length - 1; i++) {
			pathPart += path[i] + "/";

			if (addedDirectories.add(pathPart)) {
				JarEntry entry = new JarEntry(pathPart);
				entry.setTime(new Date().getTime());

				try {
					outputStream.putNextEntry(entry);
					outputStream.closeEntry();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		JarEntry entry = new JarEntry(filename);
		entry.setTime(new Date().getTime());
		entry.setSize(data.length);

		outputStream.putNextEntry(entry);
		outputStream.write(data);
		outputStream.closeEntry();
	}
}
