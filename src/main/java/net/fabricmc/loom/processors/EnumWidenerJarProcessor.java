package net.fabricmc.loom.processors;

import java.io.File;
import java.util.List;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.enumwidener.EnumWidenerTransformerEntry;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

public class EnumWidenerJarProcessor implements JarProcessor {
	private final Project project;

	private List<String> classes;

	public EnumWidenerJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public void setup() {
		this.classes = this.project.getExtensions().getByType(LoomGradleExtension.class).enumWidener;
	}

	@Override
	public void process(File file) {
		this.project.getLogger().lifecycle(String.format("EnumWidener™ v0 is in action on %s.", file));

		ZipUtil.transformEntries(file, this.classes.stream()
			.map(klass -> new ZipEntryTransformerEntry(klass.replace('.', '/') + ".class", new EnumWidenerTransformerEntry(this.project, klass)))
			.toArray(ZipEntryTransformerEntry[]::new)
		);
	}

	@Override
	public boolean isInvalid(File file) {
		return false;
	}
}
