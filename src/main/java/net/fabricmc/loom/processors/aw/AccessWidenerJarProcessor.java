package net.fabricmc.loom.processors.aw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.processors.JarProcessor;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.mappings.EntryTriple;

public class AccessWidenerJarProcessor implements JarProcessor {
	@Override
	public boolean isUpToDate(Project project, Path path) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		return Arrays.equals(ZipUtil.unpackEntry(path.toFile(), "aw.sha256"), Checksum.sha256(extension.accessWidener));
	}

	@Override
	public boolean processInput(Project project, Path from, Path to) throws IOException {
		AccessWidener accessWidener;
		File file = project.getExtensions().getByType(LoomGradleExtension.class).accessWidener;

		if (file == null) {
			return true;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			accessWidener = new AccessWidener(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read project access widener file");
		}

		Files.createDirectories(to.getParent());
		Files.copy(from, to);

		ZipUtil.transformEntries(to.toFile(), getTransformers(project.getLogger(), accessWidener));
		ZipUtil.addEntry(to.toFile(), "aw.sha256", Checksum.sha256(file));

		return false;
	}

	@Override
	public void processRemapped(Project project, RemapJarTask task, Remapper asmRemapper, Path jar) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		AccessWidener widener;

		try (BufferedReader reader = new BufferedReader(new FileReader(extension.accessWidener))) {
			widener = new AccessWidener(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read project access widener file");
		}

		AccessWidener remapped = AccessWidenerRemapper.remap(widener, asmRemapper, "intermediary");

		StringWriter writer = new StringWriter();
		remapped.write(writer);
		byte[] bytes = writer.toString().getBytes();
		writer.close();

		String path = getAccessWidenerPath(jar);

		if (path == null) {
			throw new RuntimeException("Could not find an accessWidener entry in fabric.mod.json");
		}

		if (!ZipUtil.replaceEntry(jar.toFile(), path, bytes)) {
			ZipUtil.addEntry(jar.toFile(), path, bytes);
		}
	}

	private String getAccessWidenerPath(Path modJarPath) {
		byte[] modJsonBytes = ZipUtil.unpackEntry(modJarPath.toFile(), "fabric.mod.json");

		if (modJsonBytes == null) {
			return null;
		}

		JsonObject jsonObject = new Gson().fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("accessWidener")) {
			return null;
		}

		return jsonObject.get("accessWidener").getAsString();
	}

	private ZipEntryTransformerEntry[] getTransformers(Logger logger, AccessWidener accessWidener) {
		return accessWidener.getTargets().stream()
				.map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(logger, accessWidener, string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(Logger logger, AccessWidener accessWidener, String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);

				logger.lifecycle("Applying access widener to " + className);

				reader.accept(new AccessTransformer(writer, accessWidener), 0);
				return writer.toByteArray();
			}
		};
	}

	private static class AccessTransformer extends ClassVisitor {
		private final AccessWidener accessWidener;
		private String className;
		private int classAccess;

		private AccessTransformer(ClassVisitor classVisitor, AccessWidener accessWidener) {
			super(Opcodes.ASM7, classVisitor);
			this.accessWidener = accessWidener;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			className = name;
			classAccess = access;
			super.visit(
					version,
					accessWidener.getClassAccess(name).apply(access, name, classAccess),
					name,
					signature,
					superName,
					interfaces
			);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			super.visitInnerClass(
					name,
					outerName,
					innerName,
					accessWidener.getClassAccess(name).apply(access, name, classAccess)
			);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(
					accessWidener.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
					name,
					descriptor,
					signature,
					value
			);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new AccessTransformer.AccessWidenerMethodVisitor(super.visitMethod(
					accessWidener.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
					name,
					descriptor,
					signature,
					exceptions
			));
		}

		private class AccessWidenerMethodVisitor extends MethodVisitor {
			AccessWidenerMethodVisitor(MethodVisitor methodVisitor) {
				super(Opcodes.ASM7, methodVisitor);
			}

			@Override
			public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
				if (opcode == Opcodes.INVOKESPECIAL && owner.equals(className) && !name.equals("<init>")) {
					AccessWidener.Access methodAccess = accessWidener.getMethodAccess(new EntryTriple(owner, name, descriptor));

					if (methodAccess != AccessWidener.MethodAccess.DEFAULT) {
						opcode = Opcodes.INVOKEVIRTUAL;
					}
				}

				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			}
		}
	}
}
