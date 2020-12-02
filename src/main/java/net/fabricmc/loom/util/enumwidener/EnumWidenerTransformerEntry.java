package net.fabricmc.loom.util.enumwidener;

import java.io.IOException;
import java.util.zip.ZipEntry;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;

public class EnumWidenerTransformerEntry extends ByteArrayZipEntryTransformer {
	private final Project project;
	private final String klass;

	public EnumWidenerTransformerEntry(Project project, String klass) {
		this.project = project;
		this.klass = klass;
	}

	@Override
	protected byte[] transform(ZipEntry zipEntry, byte[] input) throws IOException {
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor visitor = new EnumWidenerClassVisitor(Constants.ASM_VERSION, writer);

		this.project.getLogger().lifecycle(String.format("Applying EnumWidener™ to class %s.", klass));

		new ClassReader(input).accept(visitor, 0);

		return writer.toByteArray();
	}
}
