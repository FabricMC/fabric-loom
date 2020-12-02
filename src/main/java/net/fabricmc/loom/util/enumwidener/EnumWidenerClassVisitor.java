package net.fabricmc.loom.util.enumwidener;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class EnumWidenerClassVisitor extends ClassVisitor {
	public EnumWidenerClassVisitor(final int api, final ClassVisitor classVisitor) {
		super(api, classVisitor);
	}

	@Override
	public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
		super.visit(version, access & ~Opcodes.ACC_ENUM, name, signature, superName, interfaces);
	}
}
