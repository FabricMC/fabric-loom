package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.util.HashMap;

import dev.architectury.tinyremapper.AsmClassRemapper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.tree.MethodNode;

public class CompiledMappedClassRemapper extends ClassRemapper {
	private final MappingsCompiled compiled;
	private String lastName;
	private MethodNode lastMethod;

	public CompiledMappedClassRemapper(ClassVisitor classVisitor, MappingsCompiled compiled) {
		super(Opcodes.ASM9, classVisitor, compiled);

		this.compiled = compiled;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		lastName = name;
		this.compiled.lastSuperClass = superName;
		this.compiled.lastInterfaces = interfaces;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		lastMethod = new MethodNode(api, access, name, descriptor, signature, exceptions);
		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
		return new MethodRemapper(api, lastMethod, remapper) {
			@Override
			public void visitEnd() {
				lastMethod.localVariables = null;
				lastMethod.parameters = null;
				AsmClassRemapper.AsmMethodRemapper.processLocals(compiled, lastName, lastMethod, false, true, new HashMap<>());
				lastMethod.visitEnd();
				lastMethod.accept(methodVisitor);
			}
		};
	}
}
