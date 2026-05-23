/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.task.service;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.unpick.UnpickMetadata;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.AsyncZipProcessor;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.SLF4JAdapterHandler;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public class UnpickService extends Service<UnpickService.Options> {
	private static final Logger LOGGER = LoggerFactory.getLogger(UnpickService.class);
	private static final java.util.logging.Logger JAVA_LOGGER = java.util.logging.Logger.getLogger("loom-unpick-service");

	static {
		JAVA_LOGGER.setUseParentHandlers(false);
		JAVA_LOGGER.addHandler(new SLF4JAdapterHandler(LOGGER, true));
	}

	public static final ServiceType<Options, UnpickService> TYPE = new ServiceType<>(Options.class, UnpickService.class);

	public interface Options extends Service.Options {
		@InputFile
		@PathSensitive(PathSensitivity.NONE)
		RegularFileProperty getUnpickDefinitions();

		@Optional
		@Nested
		Property<UnpickRemapperService.Options> getUnpickRemapperService();

		@Classpath
		ConfigurableFileCollection getUnpickConstantJar();

		@Classpath
		ConfigurableFileCollection getUnpickClasspath();

		@OutputFile
		RegularFileProperty getUnpickOutputJar();

		@Input
		Property<Boolean> getLenient();
	}

	public static Provider<Options> createOptions(GenerateSourcesTask task) {
		final Project project = task.getProject();
		return TYPE.maybeCreate(project, options -> {
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();

			if (!mappingConfiguration.hasUnpickDefinitions()) {
				return false;
			}

			UnpickMetadata unpickMetadata = mappingConfiguration.getUnpickMetadata();

			if (unpickMetadata instanceof UnpickMetadata.V2 v2) {
				if (!Objects.equals(v2.namespace(), MappingsNamespace.NAMED.toString())) {
					options.getUnpickRemapperService().set(UnpickRemapperService.createOptions(project, v2));
				}
			}

			ConfigurationContainer configurations = project.getConfigurations();
			File mappingsWorkingDir = mappingConfiguration.mappingsWorkingDir().toFile();

			options.getUnpickDefinitions().set(mappingConfiguration.getUnpickDefinitionsFile());
			options.getUnpickOutputJar().set(task.getInputJarName().map(s -> project.getLayout()
					.dir(project.provider(() -> mappingsWorkingDir)).get().file(s + "-unpicked.jar")));
			options.getUnpickConstantJar().setFrom(configurations.named(Constants.Configurations.MAPPING_CONSTANTS));
			options.getUnpickClasspath().setFrom(configurations.named(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
			options.getUnpickClasspath().from(configurations.named(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED));
			options.getLenient().set(unpickMetadata instanceof UnpickMetadata.V1);
			extension.getMinecraftJars(MappingsNamespace.NAMED).forEach(options.getUnpickClasspath()::from);
			return true;
		});
	}

	public UnpickService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public Path unpickJar(Path inputJar, @Nullable Path existingClasses) throws IOException {
		final List<Path> classpath = Stream.of(
				getOptions().getUnpickClasspath().getFiles().stream().map(File::toPath),
				getOptions().getUnpickConstantJar().getFiles().stream().map(File::toPath),
				Stream.of(inputJar),
				Stream.ofNullable(existingClasses)
			).flatMap(Function.identity()).toList();
		final Path outputJar = getOptions().getUnpickOutputJar().get().getAsFile().toPath();
		Files.deleteIfExists(outputJar);

		try (ZipFsClasspath zipFsClasspath = ZipFsClasspath.create(classpath);
				InputStream unpickDefinitions = getUnpickDefinitionsInputStream()) {
			IClassResolver classResolver = zipFsClasspath.createClassResolver().chain(ClassResolvers.classpath());
			ConstantUninliner uninliner = ConstantUninliner.builder()
					.logger(JAVA_LOGGER)
					.classResolver(classResolver)
					.grouper(ConstantGroupers.dataDriven()
							.logger(JAVA_LOGGER)
							.lenient(getOptions().getLenient().get())
							.classResolver(classResolver)
							.mappingSource(unpickDefinitions)
							.build())
					.build();

			AsyncZipProcessor.processEntries(inputJar, outputJar, new UnpickZipProcessor(uninliner));
		}

		return outputJar;
	}

	private InputStream getUnpickDefinitionsInputStream() throws IOException {
		final Path unpickDefinitionsPath = getOptions().getUnpickDefinitions().getAsFile().get().toPath();

		if (getOptions().getUnpickRemapperService().isPresent()) {
			LOGGER.info("Remapping unpick definitions: {}", unpickDefinitionsPath);

			UnpickRemapperService unpickRemapperService = getServiceFactory().get(getOptions().getUnpickRemapperService());
			String remapped = unpickRemapperService.remap(unpickDefinitionsPath.toFile());

			return new ByteArrayInputStream(remapped.getBytes(StandardCharsets.UTF_8));
		}

		LOGGER.debug("Using unpick definitions: {}", unpickDefinitionsPath);

		return Files.newInputStream(unpickDefinitionsPath);
	}

	public String getUnpickCacheKey() {
		return Checksum.of(List.of(
				Checksum.of(getOptions().getUnpickDefinitions().getAsFile().get()),
				Checksum.of(getOptions().getUnpickConstantJar()),
				Checksum.of(getOptions().getUnpickRemapperService()
						.flatMap(options -> options.getTinyRemapper()
								.flatMap(TinyRemapperService.Options::getFrom))
						.getOrElse("named"))
		)).sha256().hex();
	}

	private record UnpickZipProcessor(ConstantUninliner uninliner) implements AsyncZipProcessor {
		@Override
		public void processEntryAsync(Path input, Path output) throws IOException {
			Files.createDirectories(output.getParent());

			String fileName = input.toAbsolutePath().toString();

			if (!fileName.endsWith(".class")) {
				// Copy non-class files
				Files.copy(input, output);
				return;
			}

			ClassNode classNode = new ClassNode();

			try (InputStream is = Files.newInputStream(input)) {
				ClassReader reader = new ClassReader(is);
				reader.accept(classNode, 0);
			}

			LOGGER.debug("Unpick class: {}", classNode.name);
			uninliner.transform(classNode);

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			classNode.accept(writer);

			Files.write(output, writer.toByteArray());
		}
	}

	private record ZipFsClasspath(List<FileSystemUtil.Delegate> fileSystems) implements Closeable {
		private ZipFsClasspath {
			if (fileSystems.isEmpty()) {
				throw new IllegalArgumentException("No resolvers provided");
			}
		}

		public static ZipFsClasspath create(List<Path> classpath) throws IOException {
			var fileSystems = new ArrayList<FileSystemUtil.Delegate>();

			for (Path path : classpath) {
				FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(path, false);
				fileSystems.add(fs);
			}

			return new ZipFsClasspath(fileSystems);
		}

		public IClassResolver createClassResolver() {
			IClassResolver resolver = ClassResolvers.fromDirectory(fileSystems.getFirst().getRoot());

			for (int i = 1; i < fileSystems.size(); i++) {
				resolver = resolver.chain(ClassResolvers.fromDirectory(fileSystems.get(i).getRoot()));
			}

			return resolver;
		}

		@Override
		public void close() throws IOException {
			for (FileSystemUtil.Delegate fileSystem : fileSystems) {
				fileSystem.close();
			}
		}
	}
}
