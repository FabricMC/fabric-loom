package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public enum EmptyNestedJarProvider implements NestedJarProvider {
	INSTANCE;

	@Override
	public Collection<File> provide() {
		return Collections.emptyList();
	}
}
