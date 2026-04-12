/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.mapped;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.mods.dependency.LocalMavenHelper;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.minecraft.AnnotationsApplyVisitor;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.SignatureFixerApplyVisitor;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.util.SidedClassVisitor;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class AbstractMappedMinecraftProvider<M extends MinecraftProvider> implements MappedMinecraftProvider.ProviderImpl {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMappedMinecraftProvider.class);

	protected final M minecraftProvider;
	private final Project project;
	protected final LoomGradleExtension extension;

	public AbstractMappedMinecraftProvider(Project project, M minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
		this.project = project;
		this.extension = LoomGradleExtension.get(project);
	}

	public abstract MappingsNamespace getTargetNamespace();

	/**
	 * @return A list of jars that should be remapped
	 */
	public abstract List<RemappedJars> getRemappedJars();

	/**
	 * @return A list of output jars that this provider generates
	 */
	public List<? extends OutputJar> getOutputJars() {
		return getRemappedJars();
	}

	// Returns a list of MinecraftJar.Type's that this provider exports to be used as a dependency
	public List<MinecraftJar.Type> getDependencyTypes() {
		return Collections.emptyList();
	}

	public List<MinecraftJar> provide(ProvideContext context) throws Exception {
		final List<RemappedJars> remappedJars = getRemappedJars();
		final List<MinecraftJar> minecraftJars = remappedJars.stream()
				.map(RemappedJars::outputJar)
				.toList();

		if (remappedJars.isEmpty()) {
			throw new IllegalStateException("No remapped jars provided");
		}

		if (shouldRefreshOutputs(context)) {
			try {
				remapInputs(remappedJars, context.configContext());
				createBackupJars(minecraftJars);
			} catch (Throwable t) {
				cleanOutputs(remappedJars);

				throw new RuntimeException("Failed to remap minecraft", t);
			}
		}

		if (context.applyDependencies()) {
			final List<MinecraftJar.Type> dependencyTargets = getDependencyTypes();

			if (!dependencyTargets.isEmpty()) {
				MinecraftSourceSets.get(getProject()).applyDependencies(
						(configuration, type) -> getProject().getDependencies().add(configuration, getDependencyNotation(type)),
						dependencyTargets
				);
			}
		}

		return minecraftJars;
	}

	// Create two copies of the remapped jar, the backup jar is used as the input of genSources
	public static Path getBackupJarPath(MinecraftJar minecraftJar) {
		final Path outputJarPath = minecraftJar.getPath();
		return outputJarPath.resolveSibling(outputJarPath.getFileName() + ".backup");
	}

	protected void createBackupJars(List<MinecraftJar> minecraftJars) throws IOException {
		for (MinecraftJar minecraftJar : minecraftJars) {
			Files.copy(minecraftJar.getPath(), getBackupJarPath(minecraftJar), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public record ProvideContext(boolean applyDependencies, boolean refreshOutputs, ConfigContext configContext) {
		ProvideContext withApplyDependencies(boolean applyDependencies) {
			return new ProvideContext(applyDependencies, refreshOutputs(), configContext());
		}
	}

	@Override
	public Path getJar(MinecraftJar.Type type) {
		return getMavenHelper(type).getOutputFile(null);
	}

	public enum MavenScope {
		// Output files will be stored per project
		LOCAL(LoomFiles::getLocalMinecraftRepo),
		// Output files will be stored globally
		GLOBAL(LoomFiles::getGlobalMinecraftRepo);

		private final Function<LoomFiles, File> fileFunction;

		MavenScope(Function<LoomFiles, File> fileFunction) {
			this.fileFunction = fileFunction;
		}

		public Path getRoot(LoomGradleExtension extension) {
			return fileFunction.apply(extension.getFiles()).toPath();
		}
	}

	public abstract MavenScope getMavenScope();

	public LocalMavenHelper getMavenHelper(MinecraftJar.Type type) {
		return new LocalMavenHelper("net.minecraft", getName(type), getVersion(), null, getMavenScope().getRoot(extension));
	}

	protected String getName(MinecraftJar.Type type) {
		var sj = new StringJoiner("-");
		sj.add("minecraft");
		sj.add(type.toString());

		if (!extension.disableObfuscation()) {
			// Include the intermediate mapping name if it's not the default intermediary
			final String intermediateName = extension.getIntermediateMappingsProvider().getName();

			if (!intermediateName.equals(IntermediaryMappingsProvider.NAME)) {
				sj.add(intermediateName);
			}
		} else {
			sj.add("deobf");
		}

		if (getTargetNamespace() != MappingsNamespace.NAMED) {
			sj.add(getTargetNamespace().name());
		}

		return sj.toString().toLowerCase(Locale.ROOT);
	}

	protected String getVersion() {
		if (extension.disableObfuscation()) {
			return extension.getMinecraftProvider().minecraftVersion();
		}

		return "%s-%s".formatted(extension.getMinecraftProvider().minecraftVersion(), extension.getMappingConfiguration().mappingsIdentifier());
	}

	protected String getDependencyNotation(MinecraftJar.Type type) {
		return "net.minecraft:%s:%s".formatted(getName(type), getVersion());
	}

	protected boolean shouldRefreshOutputs(ProvideContext context) {
		if (context.refreshOutputs()) {
			LOGGER.info("Refreshing outputs for mapped jar, as refresh outputs was requested");
			return true;
		}

		final List<? extends OutputJar> outputJars = getOutputJars();

		if (outputJars.isEmpty()) {
			throw new IllegalStateException("No output jars provided");
		}

		for (OutputJar outputJar : outputJars) {
			if (!getMavenHelper(outputJar.type()).exists(null)) {
				LOGGER.info("Refreshing outputs for mapped jar, as {} does not exist", outputJar.outputJar());
				return true;
			}
		}

		for (OutputJar outputJar : outputJars) {
			if (!Files.exists(getBackupJarPath(outputJar.outputJar()))) {
				LOGGER.info("Refreshing outputs for mapped jar, as backup jar does not exist for {}", outputJar.outputJar());
				return true;
			}
		}

		LOGGER.debug("All outputs are up to date");
		return false;
	}

	private void remapInputs(List<RemappedJars> remappedJars, ConfigContext configContext) throws IOException {
		cleanOutputs(remappedJars);

		for (RemappedJars remappedJar : remappedJars) {
			remapJar(remappedJar, configContext);
		}
	}

	protected void remapJar(RemappedJars remappedJars, ConfigContext configContext) throws IOException {
		if (extension.disableObfuscation()) {
			// TODO debof - can we skip this?
			Files.createDirectories(remappedJars.outputJarPath().getParent());
			Files.copy(remappedJars.inputJar(), remappedJars.outputJarPath(), StandardCopyOption.REPLACE_EXISTING);
			getMavenHelper(remappedJars.type()).savePom();
			return;
		}

		final MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();
		final String fromM = remappedJars.sourceNamespace().toString();
		final String toM = getTargetNamespace().toString();

		Files.deleteIfExists(remappedJars.outputJarPath());

		final AnnotationsData remappedAnnotations = AnnotationsData.getRemappedAnnotations(getTargetNamespace(), mappingConfiguration, getProject(), configContext.serviceFactory(), toM);
		final Map<String, String> remappedSignatures = SignatureFixerApplyVisitor.getRemappedSignatures(getTargetNamespace() == MappingsNamespace.INTERMEDIARY, mappingConfiguration, getProject(), configContext.serviceFactory(), toM);
		final MinecraftVersionMeta.JavaVersion javaVersion = minecraftProvider.getVersionInfo().javaVersion();
		final boolean fixRecords = javaVersion != null && javaVersion.majorVersion() >= 16;

		TinyRemapper remapper = TinyRemapperHelper.getTinyRemapper(getProject(), configContext.serviceFactory(), fromM, toM, fixRecords, (builder) -> {
			if (remappedAnnotations != null) {
				builder.extraPostApplyVisitor(new AnnotationsApplyVisitor(remappedAnnotations));
			}

			builder.extraPostApplyVisitor(new SignatureFixerApplyVisitor(remappedSignatures));
			configureRemapper(remappedJars, builder);
		});

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedJars.outputJarPath()).build()) {
			outputConsumer.addNonClassFiles(remappedJars.inputJar());

			for (Path path : remappedJars.remapClasspath()) {
				remapper.readClassPath(path);
			}

			remapper.readInputs(remappedJars.inputJar());
			remapper.apply(outputConsumer);
		} catch (Exception e) {
			throw new RuntimeException("Failed to remap JAR " + remappedJars.inputJar() + " with mappings from " + mappingConfiguration.tinyMappings, e);
		} finally {
			remapper.finish();
		}

		getMavenHelper(remappedJars.type()).savePom();
	}

	protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
	}

	// Configure the remapper to add the client @Environment annotation to all classes in the client jar.
	public static void configureSplitRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
		final MinecraftJar outputJar = remappedJars.outputJar();
		assert !outputJar.isMerged();

		if (outputJar.includesClient()) {
			assert !outputJar.includesServer();
			tinyRemapperBuilder.extraPostApplyVisitor(SidedClassVisitor.CLIENT);
		}
	}

	private void cleanOutputs(List<RemappedJars> remappedJars) throws IOException {
		for (RemappedJars remappedJar : remappedJars) {
			Files.deleteIfExists(remappedJar.outputJarPath());
			Files.deleteIfExists(getBackupJarPath(remappedJar.outputJar()));
		}
	}

	public Project getProject() {
		return project;
	}

	public M getMinecraftProvider() {
		return minecraftProvider;
	}

	public sealed interface OutputJar permits RemappedJars, SimpleOutputJar {
		MinecraftJar outputJar();

		default MinecraftJar.Type type() {
			return outputJar().getType();
		}
	}

	public record RemappedJars(Path inputJar, MinecraftJar outputJar, MappingsNamespace sourceNamespace, Path... remapClasspath) implements OutputJar {
		public Path outputJarPath() {
			return outputJar().getPath();
		}

		public String name() {
			return outputJar().getName();
		}
	}

	public record SimpleOutputJar(MinecraftJar outputJar) implements OutputJar {
	}
}
