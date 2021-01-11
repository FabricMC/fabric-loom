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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.google.common.collect.ObjectArrays;
import com.google.common.io.ByteStreams;
import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.constantmappers.ConstantMappers;
import daomephsta.unpick.api.constantresolvers.ConstantResolvers;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.JarClassResolver;

public class UnpickJarTask extends AbstractLoomTask {
	@InputFile
	File inputJar;
	@InputFile
	File unpickDefinition;

	@OutputFile
	File outputJar;

	public UnpickJarTask() {
		getOutputs().upToDateWhen(e -> false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		JarClassResolver minecraftClassResolver = new JarClassResolver(getMinecraftJars());
		JarClassResolver constantClassResolver = new JarClassResolver(getConstantJars());

		if (outputJar.exists()) {
			outputJar.delete();
		}

		try (InputStream unpickDefinitionStream = new FileInputStream(unpickDefinition)) {
			ConstantUninliner uninliner = new ConstantUninliner(
					ConstantMappers.dataDriven(minecraftClassResolver, unpickDefinitionStream),
					ConstantResolvers.bytecodeAnalysis(constantClassResolver)
			);

			try (JarFile jarFile = new JarFile(inputJar); JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(outputJar))) {
				Enumeration<JarEntry> entries = jarFile.entries();

				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();

					JarEntry outputEntry = new JarEntry(entry.getName());
					outputEntry.setTime(System.currentTimeMillis());

					InputStream inputStream = jarFile.getInputStream(entry);

					byte[] outputBytes;

					if (entry.getName().endsWith(".class")) {
						ClassReader classReader = new ClassReader(inputStream);
						ClassNode classNode = new ClassNode();
						classReader.accept(classNode, 0);

						uninliner.transform(classNode);

						ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
						classNode.accept(classWriter);
						outputBytes = classWriter.toByteArray();
					} else {
						outputBytes = ByteStreams.toByteArray(inputStream);
					}

					outputEntry.setSize(outputBytes.length);
					outputStream.putNextEntry(outputEntry);
					outputStream.write(outputBytes);
					outputStream.closeEntry();
				}
			}
		} finally {
			minecraftClassResolver.close();
			constantClassResolver.close();
		}
	}

	private URL[] getMinecraftJars() throws MalformedURLException {
		return ObjectArrays.concat(
			getConfigurationURLs(Constants.Configurations.MINECRAFT_DEPENDENCIES),
			new URL[]{getExtension().getMinecraftMappedProvider().getMappedJar().toURI().toURL()},
			URL.class
		);
	}

	private URL[] getConstantJars() throws MalformedURLException {
		return getConfigurationURLs(Constants.Configurations.MAPPING_CONSTANTS);
	}

	private URL[] getConfigurationURLs(String name) throws MalformedURLException {
		List<URL> list = new ArrayList<>();

		for (File file : getProject().getConfigurations().getByName(name).resolve()) {
			list.add(file.toURI().toURL());
		}

		return list.toArray(new URL[0]);
	}

	public File getInputJar() {
		return inputJar;
	}

	public UnpickJarTask setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}

	public File getUnpickDefinition() {
		return unpickDefinition;
	}

	public UnpickJarTask setUnpickDefinition(File unpickDefinition) {
		this.unpickDefinition = unpickDefinition;
		return this;
	}

	public File getOutputJar() {
		return outputJar;
	}

	public UnpickJarTask setOutputJar(File outputJar) {
		this.outputJar = outputJar;
		return this;
	}
}
