package net.fabricmc.loom.util.enumwidener;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class EnumWidenerClassNode extends ClassNode {
	public EnumWidenerClassNode(final int api) {
		super(api);
	}

	@Override
	public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
		final int widenedAccess = access & ~Opcodes.ACC_ENUM;

		if (widenedAccess == access) {
			throw new IllegalArgumentException(name + " is not an enum");
		}

		super.visit(version, widenedAccess, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
		return super.visitField(access & ~(Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC), name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
		return super.visitMethod(
			access & ~Opcodes.ACC_SYNTHETIC,
			name,
			descriptor,
			name.equals("<init>") ? signature.replace("(", "(Ljava/lang/String;I") : signature,
			exceptions
		);
	}
}
