package net.fabricmc.loom.decompilers;

import java.nio.file.Path;
import java.util.Collection;


public class DecompilationMetadata {
	public final int numberOfThreads;
	public final Path javaDocs;
	public final Collection<Path> libraries;

	public DecompilationMetadata(int numberOfThreads, Path javaDocs, Collection<Path> libraries) {
		this.numberOfThreads = numberOfThreads;
		this.javaDocs = javaDocs;
		this.libraries = libraries;
	}
}
