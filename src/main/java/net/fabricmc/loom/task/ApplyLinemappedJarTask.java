package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.task.fernflower.IncrementalDecompilation;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class ApplyLinemappedJarTask extends AbstractLoomTask {
	private Object mappedJar;
	private Object linemappedJar;
	private IncrementalDecompilation incrementalDecompilation;

	@InputFile
	public File getMappedJar() {
		return getProject().file(mappedJar);
	}

	@OutputFile
	public File getLinemappedJar() {
		return getProject().file(linemappedJar);
	}


	public void setMappedJar(Object mappedJar) {
		this.mappedJar = mappedJar;
	}

	public void setLinemappedJar(Object linemappedJar) {
		this.linemappedJar = linemappedJar;
	}

	public void setIncrementalDecompilation(IncrementalDecompilation incrementalDecompilation) {
		this.incrementalDecompilation = incrementalDecompilation;
	}

	public static Path jarsBeforeLinemapping(LoomGradleExtension extension) throws IOException {
		Path path = Paths.get(extension.getUserCache().getPath(), "jarsBeforeLinemapping");
		Files.createDirectories(path);
		return path;
	}


	@TaskAction
	public void run() {
		Path mappedJarPath = getMappedJar().toPath();
		Path linemappedJarPath = getLinemappedJar().toPath();

		if (Files.exists(linemappedJarPath)) {
			try {
				Files.createDirectories(jarsBeforeLinemapping(getExtension()));
				Path notLinemappedJar = jarsBeforeLinemapping(getExtension()).resolve(mappedJarPath.getFileName());
				// The original jars without the linemapping needs to be saved for incremental decompilation
				if (!Files.exists(notLinemappedJar)) Files.copy(mappedJarPath, notLinemappedJar);
				Files.copy(linemappedJarPath, mappedJarPath, StandardCopyOption.REPLACE_EXISTING);
				addUnchangedLinemappedFiles(mappedJarPath);

			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void addUnchangedLinemappedFiles(Path linemappedJarPath) throws IOException {
		Path closestCompiledJar = incrementalDecompilation.getOldCompiledJar();
		if (closestCompiledJar == null) return;

		Optional<Path> oldLinemappedJar = getLinemappedJarOf(closestCompiledJar);
		if (!oldLinemappedJar.isPresent()) {
			getLogger().error("Could not find linemapped jar of previously decompiled jar: " + oldLinemappedJar);
			return;
		}

		incrementalDecompilation.useUnchangedLinemappedClassFiles(oldLinemappedJar.get(), linemappedJarPath);
	}

	private Optional<Path> getLinemappedJarOf(Path compiledJar) throws IOException {
		// Note we don't use the normal mapped jar in the top-level user cache and not the so called 'linemapped' jar because
		// during the last phase of genSources the normal mapped jar gets overwritten with the linemapped one, and this way linemap changes
		// applied with incremental decompilation are also taken into account.
		return Files.list(getExtension().getUserCache().toPath())
				.filter(path -> path.getFileName().toString().equals(compiledJar.getFileName().toString()))
				.findAny();
	}


}
