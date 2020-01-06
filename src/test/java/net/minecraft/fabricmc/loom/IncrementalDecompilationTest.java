package net.minecraft.fabricmc.loom;

import net.fabricmc.loom.task.fernflower.ForkedFFExecutor;
import net.fabricmc.loom.task.fernflower.IncrementalDecompilation;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.stitch.util.StitchUtil;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class IncrementalDecompilationTest {
	private static final Path NEW_COMPILED_JAR = Paths.get("minecraft-1.15.1-build.23-not-linemapped.jar");
	private static final Path OLD_COMPILED_JAR = Paths.get("minecraft-1.15.1-build.17-not-linemapped.jar");
	private static final Path OLD_SOURCES_JAR = Paths.get("minecraft-1.15.1-build.17-sources.jar");
	private static final Path OLD_LINEMAPPED_JAR = Paths.get("minecraft-1.15.1-build.17-linemapped.jar");

	private static final Path OUT_LINEMAP = Paths.get("linemap.txt");
	private static final Path OUT_SOURCES = Paths.get("minecraft-1.15.1-build.23-sources.jar");
	private static final Path OUT_LINEMAPPED_JAR = Paths.get("minecraft-1.15.1-build.23-linemapped.jar");
	private IncrementalDecompilation ic = new IncrementalDecompilation(NEW_COMPILED_JAR, OLD_COMPILED_JAR, new TestLogger());

	@Ignore
	@Test
	public void test() throws IOException {
		Path toDecompile = ic.diffCompiledJars();

		decompile(toDecompile);

		remapLineNumbers();

		ic.addUnchangedSourceFiles(OUT_SOURCES, OLD_SOURCES_JAR);
		ic.useUnchangedLinemappedClassFiles(OLD_LINEMAPPED_JAR);
	}

	private void remapLineNumbers() throws IOException {
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(OUT_LINEMAP.toFile());

		try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(OLD_COMPILED_JAR.toFile(), true);
			 StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(OUT_LINEMAPPED_JAR.toFile(), true)) {
			remapper.process(null, inFs.get().getPath("/"), outFs.get().getPath("/"));
		}
	}

	private void decompile(Path toDecompile) {
		ForkedFFExecutor.runFF(
				new HashMap<String, Object>() {{
					put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
					put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
					put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");

				}},
				new ArrayList<>(),
				toDecompile.toFile(),
				OUT_SOURCES.toFile(),
				OUT_LINEMAP.toFile()
		);
	}

}
