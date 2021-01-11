package net.fabricmc.loom.util.srg;

import net.fabricmc.loom.LoomGradleExtension;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.util.GradleVersion;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class SpecialSourceExecutor {
	public static Path produceSrgJar(Project project, File specialSourceJar, Path officialJar, Path srgPath) throws Exception {
		Set<String> filter = Files.readAllLines(srgPath, StandardCharsets.UTF_8).stream()
				.filter(s -> !s.startsWith("\t"))
				.map(s -> s.split(" ")[0] + ".class")
				.collect(Collectors.toSet());
		Path stripped = project.getExtensions().getByType(LoomGradleExtension.class).getProjectBuildCache().toPath().resolve(officialJar.getFileName().toString().substring(0, officialJar.getFileName().toString().length() - 4) + "-filtered.jar");
		Files.deleteIfExists(stripped);
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(stripped))) {
			ZipUtil.iterate(officialJar.toFile(), (in, zipEntry) -> {
				if (filter.contains(zipEntry.getName())) {
					output.putNextEntry((ZipEntry) zipEntry.clone());
					IOUtils.write(IOUtils.toByteArray(in), output);
					output.closeEntry();
				}
			});
		}
		Path output = tmpFile();

		String[] args = new String[]{
				"--in-jar",
				stripped.toAbsolutePath().toString(),
				"--out-jar",
				output.toAbsolutePath().toString(),
				"--srg-in",
				srgPath.toAbsolutePath().toString()
		};

		JavaExec java = project.getTasks().create("PleaseIgnore_JavaExec_" + UUID.randomUUID().toString().replace("-", ""), JavaExec.class);
		java.setArgs(Arrays.asList(args));
		java.setClasspath(project.files(specialSourceJar));
		java.setWorkingDir(tmpDir().toFile());
		java.setMain("net.md_5.specialsource.SpecialSource");
		java.setStandardOutput(System.out);
		java.exec();
		if (GradleVersion.current().compareTo(GradleVersion.version("6.0.0")) >= 0) {
			java.setEnabled(false);
		} else {
			project.getTasks().remove(java);
		}

		// TODO: Figure out why doing this produces a different result? Possible different class path?
		// SpecialSource.main(args);

		Files.deleteIfExists(stripped);
		return output;
	}

	private static Path tmpFile() throws IOException {
		return Files.createTempFile(null, null);
	}

	private static Path tmpDir() throws IOException {
		return Files.createTempDirectory(null);
	}
}
