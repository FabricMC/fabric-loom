package net.fabricmc.loom.configuration.providers.minecraft.tr;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

public class CompiledMappedClassRemapper extends ClassRemapper {
	private final MappingsCompiled compiled;
	private String lastMethodName;

	public CompiledMappedClassRemapper(ClassVisitor classVisitor, MappingsCompiled compiled) {
		super(Opcodes.ASM9, classVisitor, new Remapper() {
			@Override
			public String map(String internalName) {
				return compiled.mapClass(internalName);
			}

			@Override
			public String mapFieldName(String owner, String name, String descriptor) {
				return compiled.mapField(name);
			}

			@Override
			public String mapMethodName(String owner, String name, String descriptor) {
				return compiled.mapMethod(name);
			}
		});

		this.compiled = compiled;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		lastMethodName = name;
		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
		return new MethodRemapper(api, methodVisitor, remapper) {
			@Override
			public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
				super.visitLocalVariable(compiled.mapMethodArg(lastMethodName, index, name),
						descriptor, signature, start, end, index);
			}
		};
	}
}
