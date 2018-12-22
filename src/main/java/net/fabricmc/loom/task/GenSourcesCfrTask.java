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

public class GenSourcesCfrTask extends DefaultLoomTask {
	/*
	@TaskAction
	public void genSources() throws IOException {
		Project project = this.getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = mappingsProvider.mappedProvider.getMappedJar();
		File sourcesJar = getSourcesJar(project);

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		Set<String> addedDirectories = new HashSet<>();

		try (FileOutputStream fos = new FileOutputStream(sourcesJar);
			JarOutputStream jos = new JarOutputStream(fos, manifest)) {

			project.getLogger().lifecycle(":generating sources JAR");
			CfrDriver driver = new CfrDriver.Builder()
					.withOptions(ImmutableMap.of("renameillegalidents","true"))
					.withOutputSink(new OutputSinkFactory() {
						@Override
						public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
							switch (sinkType) {
								case PROGRESS:
									return Collections.singletonList(SinkClass.STRING);
								case JAVA:
									return Collections.singletonList(SinkClass.DECOMPILED);
								default:
									return Collections.emptyList();
							}
						}

						@Override
						public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
							switch (sinkType) {
								case PROGRESS:
									return (t) -> getLogger().debug((String) t);
								case JAVA:
									//noinspection unchecked
									return (Sink<T>) new Sink<SinkReturns.Decompiled>() {
										@Override
										public void write(SinkReturns.Decompiled decompiled) {
											String filename = decompiled.getPackageName().replace('.', '/');
											if (!filename.isEmpty()) filename += "/";
											filename += decompiled.getClassName() + ".java";

											String[] path = filename.split("/");
											String pathPart = "";
											for (int i = 0; i < path.length - 1; i++) {
												pathPart += path[i] + "/";
												if (addedDirectories.add(pathPart)) {
													JarEntry entry = new JarEntry(pathPart);
													entry.setTime(new Date().getTime());

													try {
														jos.putNextEntry(entry);
														jos.closeEntry();
													} catch (IOException e) {
														throw new RuntimeException(e);
													}
												}
											}

											byte[] data = decompiled.getJava().getBytes(Charsets.UTF_8);
											JarEntry entry = new JarEntry(filename);
											entry.setTime(new Date().getTime());
											entry.setSize(data.length);

											try {
												jos.putNextEntry(entry);
												jos.write(data);
												jos.closeEntry();
											} catch (IOException e) {
												throw new RuntimeException(e);
											}
										}
									};
								default:
									return (t) -> {};
							}
						}
					})
					.build();

			driver.analyse(Collections.singletonList(mappedJar.getAbsolutePath()));
		}
	}
	*/
}
