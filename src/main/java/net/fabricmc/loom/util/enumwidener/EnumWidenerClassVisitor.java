package net.fabricmc.loom.util.enumwidener;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class EnumWidenerClassVisitor extends ClassVisitor {
	public EnumWidenerClassVisitor(final int api, final ClassVisitor classVisitor) {
		super(api, classVisitor);
	}

	private static String fixSignature(final String signature, final String name) {
//		if (name.equals("<init>")) {
//			return signature.replaceFirst("\\(", "(Ljava/lang/String;I");
//		}
//
//		return signature;
		return null;
	}

	@Override
	public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
		final int widenedAccess = access & ~Opcodes.ACC_ENUM;

		if (widenedAccess == access) {
			throw new IllegalArgumentException(name + " is not an enum");
		}

		super.visit(version, widenedAccess, name, signature, superName, interfaces);
	}

//	@Override
//	public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
//		final int widenedAccess = access & ~Opcodes.ACC_ENUM;
//
//		if (widenedAccess == access) {
//			throw new IllegalArgumentException(name + " is not an enum");
//		}
//
//		super.visitInnerClass(name, outerName, innerName, widenedAccess);
//	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
		return super.visitField(access & ~(Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC), name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
		return super.visitMethod(access & ~Opcodes.ACC_SYNTHETIC, name, descriptor, fixSignature(signature, name), exceptions);
	}
}
