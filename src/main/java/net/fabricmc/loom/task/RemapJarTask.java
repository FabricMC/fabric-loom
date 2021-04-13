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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.shedaniel.architectury.refmapremapper.RefmapRemapper;
import me.shedaniel.architectury.refmapremapper.remapper.MappingsRemapper;
import me.shedaniel.architectury.refmapremapper.remapper.ReferenceRemapper;
import me.shedaniel.architectury.refmapremapper.remapper.Remapper;
import me.shedaniel.architectury.refmapremapper.remapper.SimpleReferenceRemapper;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.JarRemapper;
import net.fabricmc.loom.build.MixinRefmapHelper;
import net.fabricmc.loom.build.NestedJars;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.util.LoggerFilter;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.loom.util.ZipReprocessorUtil;
import net.fabricmc.loom.util.gradle.GradleSupport;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public class RemapJarTask extends Jar {
	private final RegularFileProperty input;
	private final Property<Boolean> addNestedDependencies;
	private final Property<Boolean> remapAccessWidener;
	private final List<Action<TinyRemapper.Builder>> remapOptions = new ArrayList<>();
	private final Property<String> fromM;
	private final Property<String> toM;
	public JarRemapper jarRemapper;
	private FileCollection classpath;

	public RemapJarTask() {
		super();
		input = GradleSupport.getfileProperty(getProject());
		addNestedDependencies = getProject().getObjects().property(Boolean.class);
		remapAccessWidener = getProject().getObjects().property(Boolean.class);
		fromM = getProject().getObjects().property(String.class);
		toM = getProject().getObjects().property(String.class);
		fromM.set("named");
		toM.set(SourceRemapper.intermediary(getProject()));
		// false by default, I have no idea why I have to do it for this property and not the other one
		remapAccessWidener.set(false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		if (jarRemapper == null) {
			doSingleRemap();
		} else {
			scheduleRemap();
		}
	}

	public void doSingleRemap() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath();

		if (!Files.exists(input)) {
			throw new FileNotFoundException(input.toString());
		}

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String fromM = this.fromM.get();
		String toM = this.toM.get();

		Path[] classpath = getRemapClasspath();

		LoggerFilter.replaceSystemOut();
		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper();
		remapperBuilder.logger(getProject().getLogger()::lifecycle);
		remapperBuilder = remapperBuilder.withMappings(TinyRemapperMappingsHelper.create(extension.shouldGenerateSrgTiny() ? mappingsProvider.getMappingsWithSrg() : mappingsProvider.getMappings(), fromM, toM, false));

		for (File mixinMapFile : extension.getAllMixinMappings()) {
			if (mixinMapFile.exists()) {
				IMappingProvider provider = TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), fromM, "intermediary");
				remapperBuilder = remapperBuilder.withMappings(!toM.equals("intermediary") ? remapToSrg(extension, provider, fromM, toM) : provider);
			}
		}

		// Apply any requested options to tiny remapper
		for (Action<TinyRemapper.Builder> remapOption : this.remapOptions) {
			remapOption.execute(remapperBuilder);
		}

		project.getLogger().info(":remapping " + input.getFileName() + " from " + fromM + " to " + toM);

		StringBuilder rc = new StringBuilder("Remap classpath: ");

		for (Path p : classpath) {
			rc.append("\n - ").append(p.toString());
		}

		project.getLogger().debug(rc.toString());

		TinyRemapper remapper = remapperBuilder.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input);
			remapper.readClassPath(classpath);
			remapper.readInputs(input);
			remapper.apply(outputConsumer);
		} catch (Exception e) {
			remapper.finish();
			throw new RuntimeException("Failed to remap " + input + " to " + output, e);
		}

		if (getRemapAccessWidener().getOrElse(false) && extension.accessWidener != null) {
			extension.getJarProcessorManager().getByType(AccessWidenerJarProcessor.class).remapAccessWidener(output, remapper.getRemapper());
		}

		remapper.finish();

		if (!Files.exists(output)) {
			throw new RuntimeException("Failed to remap " + input + " to " + output + " - file missing!");
		}

		if (MixinRefmapHelper.addRefmapName(extension.getRefmapName(), output)) {
			project.getLogger().debug("Transformed mixin reference maps in output JAR!");
		}

		if (extension.isForge()) {
			try {
				remapRefmap(extension, output);
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap refmap jar", e);
			}
		}

		if (getAddNestedDependencies().getOrElse(false)) {
			if (NestedJars.addNestedJars(project, output)) {
				project.getLogger().debug("Added nested jar paths to mod json");
			}
		}

		if (isReproducibleFileOrder() || isPreserveFileTimestamps()) {
			ZipReprocessorUtil.reprocessZip(output.toFile(), isReproducibleFileOrder(), isPreserveFileTimestamps());
		}
	}

	private ReferenceRemapper createReferenceRemapper(LoomGradleExtension extension, String fromM, String toM) throws IOException {
		TinyTree mappings = extension.shouldGenerateSrgTiny() ? extension.getMappingsProvider().getMappingsWithSrg() : extension.getMappingsProvider().getMappings();

		return new SimpleReferenceRemapper(new SimpleReferenceRemapper.Remapper() {
			@Override
			@Nullable
			public String mapClass(String value) {
				return mappings.getClasses().stream()
						.filter(classDef -> Objects.equals(classDef.getName(fromM), value))
						.findFirst()
						.map(classDef -> classDef.getName(toM))
						.orElse(null);
			}

			@Override
			@Nullable
			public String mapMethod(@Nullable String className, String methodName, String methodDescriptor) {
				if (className != null) {
					Optional<ClassDef> classDef = mappings.getClasses().stream()
							.filter(c -> Objects.equals(c.getName(fromM), className))
							.findFirst();

					if (classDef.isPresent()) {
						for (MethodDef methodDef : classDef.get().getMethods()) {
							if (Objects.equals(methodDef.getName(fromM), methodName) && Objects.equals(methodDef.getDescriptor(fromM), methodDescriptor)) {
								return methodDef.getName(toM);
							}
						}
					}
				}

				return mappings.getClasses().stream()
						.flatMap(classDef -> classDef.getMethods().stream())
						.filter(methodDef -> Objects.equals(methodDef.getName(fromM), methodName) && Objects.equals(methodDef.getDescriptor(fromM), methodDescriptor))
						.findFirst()
						.map(methodDef -> methodDef.getName(toM))
						.orElse(null);
			}

			@Override
			@Nullable
			public String mapField(@Nullable String className, String fieldName, String fieldDescriptor) {
				if (className != null) {
					Optional<ClassDef> classDef = mappings.getClasses().stream()
							.filter(c -> Objects.equals(c.getName(fromM), className))
							.findFirst();

					if (classDef.isPresent()) {
						for (FieldDef fieldDef : classDef.get().getFields()) {
							if (Objects.equals(fieldDef.getName(fromM), fieldName) && Objects.equals(fieldDef.getDescriptor(fromM), fieldDescriptor)) {
								return fieldDef.getName(toM);
							}
						}
					}
				}

				return mappings.getClasses().stream()
						.flatMap(classDef -> classDef.getFields().stream())
						.filter(fieldDef -> Objects.equals(fieldDef.getName(fromM), fieldName) && Objects.equals(fieldDef.getDescriptor(fromM), fieldDescriptor))
						.findFirst()
						.map(fieldDef -> fieldDef.getName(toM))
						.orElse(null);
			}
		});
	}

	public void scheduleRemap() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath();

		if (!Files.exists(input)) {
			throw new FileNotFoundException(input.toString());
		}

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String fromM = this.fromM.get();
		String toM = this.toM.get();

		if (extension.isRootProject()) {
			jarRemapper.addToClasspath(getRemapClasspath());

			jarRemapper.addMappings(TinyRemapperMappingsHelper.create(extension.shouldGenerateSrgTiny() ? mappingsProvider.getMappingsWithSrg() : mappingsProvider.getMappings(), fromM, toM, false));
		}

		for (File mixinMapFile : extension.getAllMixinMappings()) {
			if (mixinMapFile.exists()) {
				IMappingProvider provider = TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), fromM, "intermediary");
				jarRemapper.addMappings(!toM.equals("intermediary") ? remapToSrg(extension, provider, fromM, toM) : provider);
			}
		}

		// Add remap options to the jar remapper
		jarRemapper.addOptions(this.remapOptions);

		project.getLogger().info(":scheduling remap " + input.getFileName() + " from " + fromM + " to " + toM);

		jarRemapper.scheduleRemap(input, output)
				.supplyAccessWidener((remapData, remapper) -> {
					if (getRemapAccessWidener().getOrElse(false) && extension.accessWidener != null) {
						AccessWidenerJarProcessor accessWidenerJarProcessor = extension.getJarProcessorManager().getByType(AccessWidenerJarProcessor.class);
						byte[] data;

						try {
							data = accessWidenerJarProcessor.getRemappedAccessWidener(remapper);
						} catch (IOException e) {
							throw new RuntimeException("Failed to remap access widener");
						}

						String awPath = accessWidenerJarProcessor.getAccessWidenerPath(remapData.input);
						Preconditions.checkNotNull(awPath, "Failed to find accessWidener in fabric.mod.json: " + remapData.input);

						return Pair.of(awPath, data);
					}

					return null;
				})
				.complete((data, accessWidener) -> {
					if (!Files.exists(output)) {
						throw new RuntimeException("Failed to remap " + input + " to " + output + " - file missing!");
					}

					if (MixinRefmapHelper.addRefmapName(extension.getRefmapName(), output)) {
						project.getLogger().debug("Transformed mixin reference maps in output JAR!");
					}

					if (extension.isForge()) {
						try {
							remapRefmap(extension, output);
						} catch (IOException e) {
							throw new RuntimeException("Failed to remap refmap jar", e);
						}
					}

					if (getAddNestedDependencies().getOrElse(false)) {
						if (NestedJars.addNestedJars(project, output)) {
							project.getLogger().debug("Added nested jar paths to mod json");
						}
					}

					if (accessWidener != null) {
						boolean replaced = ZipUtil.replaceEntry(data.output.toFile(), accessWidener.getLeft(), accessWidener.getRight());
						Preconditions.checkArgument(replaced, "Failed to remap access widener");
					}
				});
	}

	private void remapRefmap(LoomGradleExtension extension, Path output) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), ImmutableMap.of("create", false))) {
			Path refmapPath = fs.getPath(extension.getRefmapName());

			if (Files.exists(refmapPath)) {
				try (Reader refmapReader = Files.newBufferedReader(refmapPath, StandardCharsets.UTF_8)) {
					Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					JsonObject refmapElement = gson.fromJson(refmapReader, JsonObject.class);
					refmapElement = RefmapRemapper.remap(new Remapper() {
						ReferenceRemapper remapper = createReferenceRemapper(extension, fromM.get(), toM.get());

						@Override
						@Nullable
						public MappingsRemapper remapMappings() {
							return className -> remapper;
						}

						@Override
						@Nullable
						public Map.Entry<String, @Nullable MappingsRemapper> remapMappingsData(String data) {
							if (Objects.equals(data, "named:intermediary")) {
								return new AbstractMap.SimpleEntry<>("searge", remapMappings());
							}

							return null;
						}
					}, refmapElement);
					Files.delete(refmapPath);
					Files.write(refmapPath, gson.toJson(refmapElement).getBytes(StandardCharsets.UTF_8));
				}
			}
		}
	}

	private IMappingProvider remapToSrg(LoomGradleExtension extension, IMappingProvider parent, String fromM, String toM) throws IOException {
		TinyTree mappings = extension.shouldGenerateSrgTiny() ? extension.getMappingsProvider().getMappingsWithSrg() : extension.getMappingsProvider().getMappings();

		return sink -> {
			parent.load(new IMappingProvider.MappingAcceptor() {
				@Override
				public void acceptClass(String srcName, String dstName) {
					String srgName = mappings.getClasses()
							.stream()
							.filter(it -> Objects.equals(it.getName(fromM), dstName))
							.findFirst()
							.map(it -> it.getName(toM))
							.orElse(dstName);
					sink.acceptClass(srcName, srgName);
				}

				@Override
				public void acceptMethod(IMappingProvider.Member method, String dstName) {
					String srgName = mappings.getClasses()
							.stream()
							.flatMap(it -> it.getMethods().stream())
							.filter(it -> Objects.equals(it.getName(fromM), dstName))
							.findFirst()
							.map(it -> it.getName(toM))
							.orElse(dstName);
					sink.acceptMethod(method, srgName);
				}

				@Override
				public void acceptField(IMappingProvider.Member field, String dstName) {
					String srgName = mappings.getClasses()
							.stream()
							.flatMap(it -> it.getFields().stream())
							.filter(it -> Objects.equals(it.getName(fromM), dstName))
							.findFirst()
							.map(it -> it.getName(toM))
							.orElse(dstName);
					sink.acceptField(field, srgName);
				}

				@Override
				public void acceptMethodArg(IMappingProvider.Member method, int lvIndex, String dstName) {
				}

				@Override
				public void acceptMethodVar(IMappingProvider.Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
				}
			});
		};
	}

	private Path[] getRemapClasspath() {
		FileCollection files = this.classpath;

		if (files == null) {
			files = getProject().getConfigurations().getByName("compileClasspath");
		}

		return files.getFiles().stream()
				.map(File::toPath)
				.filter(Files::exists)
				.toArray(Path[]::new);
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}

	@Input
	public Property<Boolean> getAddNestedDependencies() {
		return addNestedDependencies;
	}

	@Input
	public Property<Boolean> getRemapAccessWidener() {
		return remapAccessWidener;
	}

	public void remapOptions(Action<TinyRemapper.Builder> action) {
		this.remapOptions.add(action);
	}

	public RemapJarTask classpath(FileCollection collection) {
		if (this.classpath == null) {
			this.classpath = collection;
		} else {
			this.classpath = this.classpath.plus(collection);
		}

		return this;
	}

	@Input
	public Property<String> getFromM() {
		return fromM;
	}

	@Input
	public Property<String> getToM() {
		return toM;
	}
}
