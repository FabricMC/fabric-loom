package net.fabricmc.loom.util.proccessing;

import com.google.common.collect.Streams;
import cuchaz.enigma.Deobfuscator;
import javassist.CtClass;
import javassist.CtMember;
import javassist.bytecode.AccessFlag;

import java.util.Arrays;

public class ClassAccessManipulator implements Deobfuscator.ClassTransformer {

	AccessManipulatorData data;

	public ClassAccessManipulator(AccessManipulatorData data) {
		this.data = data;
	}

	public CtClass transform(CtClass ctClass) {
		data.classData.stream().filter(classData -> classData.isEqual(ctClass.getName()))
			.forEach(classData -> Streams.concat(classData.getMethods().stream(), classData.getFields().stream())
			.forEach(accessData -> Streams.concat(Arrays.stream(ctClass.getDeclaredBehaviors()), Arrays.stream(ctClass.getDeclaredFields()))
			.filter(member -> accessData.isEqual(AccessManipulatorUtils.getMemberName(member)))
			.forEach(member -> setAccess(member, accessData.access))));
		return ctClass;
	}

	private void setAccess(CtMember member, AccessManipulatorData.Access access) {
		switch (access) {
			case PUBLIC:
				if (!AccessFlag.isPublic(member.getModifiers())) {
					member.setModifiers(AccessFlag.setPublic(member.getModifiers()));
				}
				break;
			case PRIVATE:
				if (!AccessFlag.isPrivate(member.getModifiers())) {
					member.setModifiers(AccessFlag.setPrivate(member.getModifiers()));
				}
				break;
			case PROTECTED:
				if (!AccessFlag.isProtected(member.getModifiers())) {
					member.setModifiers(AccessFlag.setProtected(member.getModifiers()));
				}
				break;
			default:
		}
	}

}
