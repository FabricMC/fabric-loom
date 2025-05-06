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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.SLF4JAdapterHandler;
import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

public class UnpickService extends Service<UnpickService.Options> {
	public static final ServiceType<Options, UnpickService> TYPE = new ServiceType<>(Options.class, UnpickService.class);

	public interface Options extends Service.Options {
		@InputFile
		RegularFileProperty getUnpickDefinitions();

		@InputFiles
		ConfigurableFileCollection getUnpickConstantJar();

		@InputFiles
		ConfigurableFileCollection getUnpickClasspath();

		@InputFiles
		ConfigurableFileCollection getUnpickRuntimeClasspath();

		@OutputFile
		RegularFileProperty getUnpickOutputJar();
	}

	public static Provider<Options> createOptions(GenerateSourcesTask task) {
		final Project project = task.getProject();
		return TYPE.maybeCreate(project, options -> {
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();

			if (!mappingConfiguration.hasUnpickDefinitions()) {
				return false;
			}

			ConfigurationContainer configurations = project.getConfigurations();
			File mappingsWorkingDir = mappingConfiguration.mappingsWorkingDir().toFile();

			options.getUnpickRuntimeClasspath().from(configurations.getByName(Constants.Configurations.UNPICK_CLASSPATH));
			options.getUnpickDefinitions().set(mappingConfiguration.getUnpickDefinitionsFile());
			options.getUnpickOutputJar().set(task.getInputJarName().map(s -> project.getLayout()
					.dir(project.provider(() -> mappingsWorkingDir)).get().file(s + "-unpicked.jar")));
			options.getUnpickConstantJar().setFrom(configurations.getByName(Constants.Configurations.MAPPING_CONSTANTS));
			options.getUnpickClasspath().setFrom(configurations.getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES));
			options.getUnpickClasspath().from(configurations.getByName(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED));
			extension.getMinecraftJars(MappingsNamespace.NAMED).forEach(options.getUnpickClasspath()::from);
			return true;
		});
	}

	public UnpickService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	public Path unpickJar(WorkerExecutor workerExecutor, Path inputJar, @Nullable Path existingClasses) {
		final Path outputJar = getOptions().getUnpickOutputJar().get().getAsFile().toPath();
		final List<String> args = getUnpickArgs(inputJar, outputJar, existingClasses);

		WorkQueue workQueue = workerExecutor.classLoaderIsolation(spec -> {
			spec.getClasspath().from(getOptions().getUnpickRuntimeClasspath());
		});

		workQueue.submit(UnpickAction.class, params -> {
			params.getMainClass().set("daomephsta.unpick.cli.Main");
			params.getArgs().set(args);
		});

		workQueue.await();

		return outputJar;
	}

	private List<String> getUnpickArgs(Path inputJar, Path outputJar, @Nullable Path existingClasses) {
		var fileArgs = new ArrayList<File>();

		fileArgs.add(inputJar.toFile());
		fileArgs.add(outputJar.toFile());
		fileArgs.add(getOptions().getUnpickDefinitions().get().getAsFile());
		fileArgs.add(getOptions().getUnpickConstantJar().getSingleFile());

		for (File file : getOptions().getUnpickClasspath()) {
			fileArgs.add(file);
		}

		if (existingClasses != null) {
			fileArgs.add(existingClasses.toFile());
		}

		return fileArgs.stream()
				.map(File::getAbsolutePath)
				.toList();
	}

	public String getUnpickCacheKey() {
		return Checksum.of(List.of(
				Checksum.of(getOptions().getUnpickDefinitions().getAsFile().get()),
				Checksum.of(getOptions().getUnpickConstantJar()),
				Checksum.of(getOptions().getUnpickRuntimeClasspath())
		)).sha256().hex();
	}

	public interface UnpickParams extends WorkParameters {
		Property<String> getMainClass();
		ListProperty<String> getArgs();
	}

	public abstract static class UnpickAction implements WorkAction<UnpickParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(UnpickAction.class);

		@Override
		public void execute() {
			java.util.logging.Logger logger = java.util.logging.Logger.getLogger("unpick");
			logger.setUseParentHandlers(false);
			logger.addHandler(new SLF4JAdapterHandler(LOGGER, true));

			try {
				Class<?> unpickEntrypoint = Class.forName(getParameters().getMainClass().get());
				unpickEntrypoint.getMethod("main", String[].class)
						.invoke(null, (Object) getParameters().getArgs().get().toArray(String[]::new));
			} catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
				throw new RuntimeException("Failed to run unpick", e);
			}
		}
	}
}
