package net.fabricmc.loom.util.proccessing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cuchaz.enigma.Deobfuscator;
import javassist.*;
import javassist.bytecode.AccessFlag;
import net.fabricmc.loom.task.MapJarsTask;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.impldep.com.google.common.base.Charsets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class AccessManipulatorUtils {

	private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void saveAMJson(AccessManipulatorData manipulatorData, File file) throws IOException {
		FileUtils.writeStringToFile(file, GSON.toJson(manipulatorData), Charsets.UTF_8);
	}

	public static AccessManipulatorData readAMFromFile(File file) throws IOException {
		return GSON.fromJson(FileUtils.readFileToString(file, Charsets.UTF_8), AccessManipulatorData.class);
	}

	public static AccessManipulatorData.ClassData getClassData(CtClass ctClass){
		AccessManipulatorData.ClassData classData = new AccessManipulatorData.ClassData();
		classData.name = ctClass.getName();
		classData.members = new ArrayList<>();
		Stream.concat(Arrays.stream(ctClass.getDeclaredMethods()), Arrays.stream(ctClass.getDeclaredFields())).forEach(ctMember -> {
			AccessManipulatorData.MemberData memberData = new AccessManipulatorData.MemberData();
			memberData.name = ctMember.getName();
			if(!(ctMember instanceof CtField)){
				memberData.desc = ctMember.getSignature();
			}
			memberData.acc = getaccesss(ctMember).name();
			classData.members.add(memberData);
		});
		return classData;
	}

	public static boolean isEqual(AccessManipulatorData.MemberData memberData, CtMember member){
		if(member instanceof CtField){
			return Arrays.asList(memberData.name.split("\\|")).contains(member.getName());
		}
		return memberData.name != null && memberData.desc != null && Arrays.asList(memberData.name.split("\\|")).contains(member.getName()) && Arrays.asList(memberData.desc.split("\\|")).contains(member.getSignature());
	}


	public static AccessManipulatorData.Access getaccesss(CtMember ctMember){
		if(AccessFlag.isPublic(ctMember.getModifiers())){
			return AccessManipulatorData.Access.PUBLIC;
		}
		if(AccessFlag.isProtected(ctMember.getModifiers())){
			return AccessManipulatorData.Access.PROTECTED;
		}
		if(AccessFlag.isPrivate(ctMember.getModifiers())){
			return AccessManipulatorData.Access.PRIVATE;
		}
		return AccessManipulatorData.Access.DEAFULT;
	}

	public static AccessManipulatorData merge(AccessManipulatorData data1, AccessManipulatorData data2){
		//TODO merge two sets of data
		return null;
	}

	// Code used to test outside of gradle
	public static void buildDataForJar(File jarFile) throws IOException {
		AccessManipulatorData data = new AccessManipulatorData();
		data.data = new ArrayList<>();
		Deobfuscator deobfuscator = new Deobfuscator(new JarFile(jarFile));
		deobfuscator.transformJar(new File(jarFile.getName() + ".export.jar"), new MapJarsTask.ProgressListener(), ctClass -> {
			data.data.add(getClassData(ctClass));
			System.out.println("Loaded: " + ctClass.getName());
			return ctClass;
		});
		ClassAccessManipulator classAccessManipulator = new ClassAccessManipulator(readAMFromFile(new File("exportedData2.json")));
		deobfuscator.transformJar(new File(jarFile.getName() + ".export2.jar"), new MapJarsTask.ProgressListener(), classAccessManipulator);
		saveAMJson(data, new File("exportedData.json"));
	}

	public static void main(String[] args) throws IOException {
		buildDataForJar(new File(args[0]));
	}

}
