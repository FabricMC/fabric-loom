package net.fabricmc.loom.api.remapping;

import javax.inject.Inject;

import org.objectweb.asm.ClassVisitor;

public interface RemapperExtension<T extends RemapperParameters> {
	@Inject
	T getParameters();

	ClassVisitor insertVisitor(String className, RemapperContext remapperContext, ClassVisitor classVisitor);
}
