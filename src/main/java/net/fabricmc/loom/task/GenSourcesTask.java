/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.task;

import com.google.common.io.ByteStreams;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftLibraryProvider;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.*;

public class GenSourcesTask extends DefaultLoomTask {
	public static File getSourcesJar(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		String path = mappedJar.getAbsolutePath();
		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + "-sources.jar");
	}

	@TaskAction
	public void genSources() throws IOException {
		Project project = this.getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MinecraftLibraryProvider libraryProvider = extension.getMinecraftProvider().libraryProvider;
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		File sourcesJar = getSourcesJar(project);

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

		project.getLogger().lifecycle(":preparing sources JAR");
		Map<String, Object> options = new HashMap<>();
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");

		LoomFernflowerDecompiler decompiler = new LoomFernflowerDecompiler(sourcesJar.getParentFile(), sourcesJar.getName(), options, new LoomFernflowerLogger());
		decompiler.addSource(mappedJar);
		for (File lib : libraryProvider.getLibraries()) {
			try {
				decompiler.addLibrary(lib);
			} catch (Exception e) {
				// pass
			}
		}

		project.getLogger().lifecycle(":generating sources JAR");
		decompiler.decompileContext();

		Map<String, int[]> mapNumbers = decompiler.getDifferingMappings();
		if (!mapNumbers.isEmpty()) {
			project.getLogger().lifecycle(":readjusting line numbers");

			File tmpJar = new File(mappedJar.getAbsolutePath() + ".tmp");
			mappedJar.renameTo(tmpJar);
			try (
					FileInputStream fis = new FileInputStream(tmpJar);
					JarInputStream jis = new JarInputStream(fis);
					FileOutputStream fos = new FileOutputStream(mappedJar);
					JarOutputStream jos = new JarOutputStream(fos)
					) {
				JarEntry entry;

				while ((entry = jis.getNextJarEntry()) != null) {
					JarEntry outputEntry = new JarEntry(entry.getName());
					outputEntry.setTime(entry.getTime());
					outputEntry.setCreationTime(entry.getCreationTime());
					outputEntry.setLastAccessTime(entry.getLastAccessTime());
					outputEntry.setLastModifiedTime(entry.getLastModifiedTime());

					if (!entry.getName().endsWith(".class")) {
						jos.putNextEntry(outputEntry);
						ByteStreams.copy(jis, jos);
						jos.closeEntry();
					} else {
						String idx = entry.getName().substring(0, entry.getName().length() - 6);
						int dollarPos = idx.indexOf('$');
						if (dollarPos >= 0) {
							idx = idx.substring(0, dollarPos);
						}

						byte[] data = ByteStreams.toByteArray(jis);
						if (mapNumbers.containsKey(idx)) {
							ClassReader reader = new ClassReader(data);
							ClassWriter writer = new ClassWriter(0);

							reader.accept(new LineNumberAdjustmentVisitor(Opcodes.ASM7, writer, mapNumbers.get(idx)), 0);
							data = writer.toByteArray();
						}

						jos.putNextEntry(outputEntry);
						jos.write(data);
						jos.closeEntry();
					}
				}
			}

			//noinspection ResultOfMethodCallIgnored
			tmpJar.delete();
		}
	}
}
