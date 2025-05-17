/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration.mods.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

public record LocalMavenHelper(String group, String name, String version, @Nullable String baseClassifier, Path root) {
	// https://github.com/gradle/gradle/blob/f65ba071aebbdbcebd1cafc0ee4cfa9e423d7b4c/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/artifacts/repositories/resolver/MavenResolver.java#L57
	private static final Pattern SNAPSHOT_VERSION_PATTERN = Pattern.compile("(?:.+)-(\\d{8}\\.\\d{6}-\\d+)");

	public Path copyToMaven(Path artifact, @Nullable String classifier) throws IOException {
		if (!artifact.getFileName().toString().endsWith(".jar")) {
			throw new UnsupportedOperationException();
		}

		Files.createDirectories(getDirectory());
		savePom();
		return Files.copy(artifact, getOutputFile(classifier), StandardCopyOption.REPLACE_EXISTING);
	}

	public boolean exists(String classifier) {
		return Files.exists(getOutputFile(classifier)) && Files.exists(getPomPath());
	}

	public String getNotation() {
		if (baseClassifier != null) {
			return String.format("%s:%s:%s:%s", group, name, version, baseClassifier);
		}

		return String.format("%s:%s:%s", group, name, version);
	}

	public void savePom() {
		try {
			String pomTemplate;

			try (InputStream input = ModDependency.class.getClassLoader().getResourceAsStream("mod_compile_template.pom")) {
				pomTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			}

			pomTemplate = pomTemplate
					.replace("%GROUP%", group)
					.replace("%NAME%", name)
					.replace("%VERSION%", version);

			Files.writeString(getPomPath(), pomTemplate, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write mod pom", e);
		}
	}

	private Path getDirectory() {
		String version = this.version;
		Matcher matcher = SNAPSHOT_VERSION_PATTERN.matcher(version);

		// Handle depending on specific snapshot versions by removing the timestamp and publish count from the version used for the directory
		if (matcher.matches()) {
			version = matcher.group("version") + "-SNAPSHOT";
		}

		return root.resolve("%s/%s/%s".formatted(group.replace(".", "/"), name, version));
	}

	private Path getPomPath() {
		return getDirectory().resolve("%s-%s.pom".formatted(name, version));
	}

	public Path getOutputFile(@Nullable String classifier) {
		if (classifier == null) {
			classifier = baseClassifier;
		}

		final String fileName = classifier == null ? String.format("%s-%s.jar", name, version)
													: String.format("%s-%s-%s.jar", name, version, classifier);
		return getDirectory().resolve(fileName);
	}

	public LocalMavenHelper withClassifier(String classifier) {
		return new LocalMavenHelper(group, name, version, classifier, root);
	}
}
