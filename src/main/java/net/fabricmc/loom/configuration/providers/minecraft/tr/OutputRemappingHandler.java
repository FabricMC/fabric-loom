package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;

import dev.architectury.tinyremapper.InputTag;
import dev.architectury.tinyremapper.TinyRemapper;

import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.FileSystemUtil.FileSystemDelegate;
import net.fabricmc.loom.util.ThreadingUtils;

public class OutputRemappingHandler {
	public static void remap(TinyRemapper remapper, Path assets, Path output) throws IOException {
		remap(remapper, assets, output, null);
	}

	public static void remap(TinyRemapper remapper, Path assets, Path output, BiConsumer<String, byte[]> then) throws IOException {
		remap(remapper, assets, output, then, (InputTag[]) null);
	}

	public static void remap(TinyRemapper remapper, Path assets, Path output, BiConsumer<String, byte[]> then, InputTag... inputTags) throws IOException {
		Files.copy(assets, output, StandardCopyOption.REPLACE_EXISTING);

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

					if (then != null) {
						then.accept(path, bytes);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, inputTags);

			taskCompleter.complete();
		}
	}
}
