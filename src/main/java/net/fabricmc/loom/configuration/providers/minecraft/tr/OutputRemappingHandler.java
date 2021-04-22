package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;

import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.FileSystemUtil.FileSystemDelegate;
import net.fabricmc.loom.util.ThreadingUtils;
import net.fabricmc.tinyremapper.TinyRemapper;

public class OutputRemappingHandler {
	public static void remap(TinyRemapper remapper, Path input, Path output) throws IOException {
		remap(remapper, input, output, (path, bytes) -> {
		});
	}

	public static void remap(TinyRemapper remapper, Path input, Path output, BiConsumer<String, byte[]> then) throws IOException {
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);

		try (FileSystemDelegate system = FileSystemUtil.getJarFileSystem(output, true)) {
			ThreadingUtils.TaskCompleter taskCompleter = ThreadingUtils.taskCompleter();

			remapper.apply((path, bytes) -> {
				if (path.startsWith("/")) path = path.substring(1);

				try {
					Path fsPath = system.get().getPath(path + ".class");

					if (fsPath.getParent() != null) {
						Files.createDirectories(fsPath.getParent());
					}

					taskCompleter.add(() -> {
						Files.write(fsPath, bytes, StandardOpenOption.CREATE);
					});

					then.accept(path, bytes);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});

			taskCompleter.complete();
		}
	}
}
