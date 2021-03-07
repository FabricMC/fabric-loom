package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class MergedNestedJarProvider implements NestedJarProvider {
	private final NestedJarProvider[] parents;

	public MergedNestedJarProvider(NestedJarProvider... parents) {
		this.parents = parents;
	}

	@Override
	public Collection<File> provide() {
		return Arrays.stream(parents)
				.map(NestedJarProvider::provide)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}
}
