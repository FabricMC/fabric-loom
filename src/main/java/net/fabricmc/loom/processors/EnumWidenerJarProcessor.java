package net.fabricmc.loom.processors;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.enumwidener.EnumWidenerTransformerEntry;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

public class EnumWidenerJarProcessor implements JarProcessor {
	private final Project project;
	private final LoomGradleExtension loom;
	private final File hashFile;

	private List<String> classes;
	private int hash;

	public EnumWidenerJarProcessor(Project project) {
		this.project = project;
		this.loom = project.getExtensions().getByType(LoomGradleExtension.class);
		this.hashFile = new File(this.loom.getProjectPersistentCache(), "ew.hash");
	}

	@Override
	public void setup() {
		this.classes = this.loom.enumWidener;

		try {
			if (this.hashFile.exists()) {
				this.hash = ByteBuffer.wrap(IOUtils.toByteArray(this.hashFile.toURI())).getInt();
			}
		} catch (IOException ignored) {}
	}

	@Override
	public void process(File file) {
		try {
			if (file.equals(this.loom.getMappingsProvider().mappedProvider.getMappedJar())) {
				this.project.getLogger().lifecycle(String.format("EnumWidener(tm) v0 is in action on %s.", file));

				ZipUtil.transformEntries(file, this.classes.stream()
					.map(klass -> new ZipEntryTransformerEntry(klass.replace('.', '/') + ".class", new EnumWidenerTransformerEntry(this.project, klass)))
					.toArray(ZipEntryTransformerEntry[]::new)
				);

				Files.write(ByteBuffer.allocate(4).putInt(this.classes.hashCode()).array(), this.hashFile);
			}
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	@Override
	public boolean isInvalid(File file) {
		if (this.classes == null || !file.equals(this.loom.getMappingsProvider().mappedProvider.getMappedJar())) {
			return false;
		}

		return this.hash == 0 || this.hash != this.classes.hashCode();
	}
}
