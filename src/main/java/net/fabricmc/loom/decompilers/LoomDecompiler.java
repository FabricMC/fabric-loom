package net.fabricmc.loom.decompilers;

import java.nio.file.Path;


public interface LoomDecompiler {
	String name();

	/**
	 * @param sourcesDestination Decompiled sources jar
	 * @param linemapDestination A byproduct of decompilation that lines up the compiled jar's line numbers with the decompiled
	 *                           sources jar for debugging.
	 *                           A decompiler may not produce a linemap at all.
	 * @param metaData Additional information that may or may not be needed while decompiling
	 */
	void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData);
}
