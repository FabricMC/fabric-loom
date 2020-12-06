package net.fabricmc.loom.util.enumwidener;

import java.util.List;
import java.util.zip.ZipEntry;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;

public class EnumWidenerTransformerEntry extends ByteArrayZipEntryTransformer {
	private final Project project;
	private final String klass;

	public EnumWidenerTransformerEntry(Project project, String klass) {
		this.project = project;
		this.klass = klass;
	}

	@SuppressWarnings("unchecked")
	private static List<AnnotationNode>[] shiftParameterAnnotations(List<AnnotationNode>[] annotations) {
		int annotationCount = annotations.length;
		List<AnnotationNode>[] shiftedAnnotations = new List[annotationCount + 2];

		System.arraycopy(annotations, 0, shiftedAnnotations, 2, annotationCount);

		return shiftedAnnotations;
	}

	@Override
	protected byte[] transform(ZipEntry zipEntry, byte[] input) {
		ClassWriter writer = new ClassWriter(0);
		ClassNode node = new EnumWidenerClassNode(Constants.ASM_VERSION);

		this.project.getLogger().lifecycle(String.format("Applying EnumWidener(tm) to %s.", klass));

		new ClassReader(input).accept(node, 0);

		for (MethodNode method : node.methods) {
			if (method.name.equals("<init>")) {
				if (method.invisibleAnnotableParameterCount > 0) {
					method.invisibleParameterAnnotations = shiftParameterAnnotations(method.invisibleParameterAnnotations);
					method.invisibleAnnotableParameterCount += 2;
				}

				if (method.visibleAnnotableParameterCount > 0) {
					method.visibleParameterAnnotations = shiftParameterAnnotations(method.visibleParameterAnnotations);
					method.visibleAnnotableParameterCount += 2;
				}
			}
		}

		node.accept(writer);

		return writer.toByteArray();
	}
}
