package net.fabricmc.loom.processors;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.enumwidener.EnumWidenerTransformerEntry;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

public class EnumWidenerJarProcessor implements JarProcessor {
	private static final String HASH_FILE_NAME = "ew.hash";

	private final Project project;
	private final LoomGradleExtension loom;

	private List<String> classes;

	public EnumWidenerJarProcessor(Project project) {
		this.project = project;
		this.loom = project.getExtensions().getByType(LoomGradleExtension.class);
	}

	@Override
	public void setup() {
		this.classes = this.loom.enumWidener;
	}

	@Override
	public void process(File file) {
		if (this.classes != null && file.equals(this.getTarget())) {
			this.project.getLogger().lifecycle(String.format("EnumWidener(tm) v0 is in action on %s.", file));

			ZipUtil.transformEntries(file, this.classes.stream()
				.map(klass -> new ZipEntryTransformerEntry(klass.replace('.', '/') + ".class", new EnumWidenerTransformerEntry(this.project, klass)))
				.toArray(ZipEntryTransformerEntry[]::new)
			);

			ZipUtil.addEntry(file, HASH_FILE_NAME, ByteBuffer.allocate(4).putInt(this.classes.hashCode()).array());
		}
	}

	@Override
	public boolean isInvalid(File file) {
		if (this.classes == null || !file.equals(this.getTarget())) {
			return false;
		}

		final byte[] hash = ZipUtil.unpackEntry(file, HASH_FILE_NAME);

		return hash == null || ByteBuffer.wrap(hash).getInt() != this.classes.hashCode();
	}

	private File getTarget() {
		return this.loom.getMappingsProvider().mappedProvider.getMappedJar();
	}
}
