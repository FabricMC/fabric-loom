package net.fabricmc.loom.api.remapping;

import org.objectweb.asm.commons.Remapper;

public interface RemapperContext {
	Remapper getRemapper();

	String sourceNamespace();

	String targetNamespace();
}
