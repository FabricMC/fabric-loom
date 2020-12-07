package net.fabricmc.loom.util.enumwidener;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

public class EnumWidenerMethodVisitor extends MethodVisitor {
	private final boolean constructor;

	public EnumWidenerMethodVisitor(int api, MethodVisitor methodVisitor, boolean constructor) {
		super(api, methodVisitor);

		this.constructor = constructor;
	}

	@Override
	public void visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
		super.visitAnnotableParameterCount(!this.constructor || parameterCount == 0 ? parameterCount : parameterCount + 2, visible);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(final int parameter, final String descriptor, final boolean visible) {
		return super.visitParameterAnnotation(this.constructor ? parameter + 2 : parameter, descriptor, visible);
	}
}
