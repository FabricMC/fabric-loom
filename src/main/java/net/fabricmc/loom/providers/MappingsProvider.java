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

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.Version;
import net.fabricmc.mappings.Mappings;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandMergeTiny;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.stitch.commands.CommandReorderTiny;
import net.fabricmc.stitch.commands.tinyv2.CommandMergeTinyV2;
import net.fabricmc.stitch.commands.tinyv2.CommandProposeV2FieldNames;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;
import net.fabricmc.tinyv2.TinyMetadata;
import net.fabricmc.tinyv2.TinyV2Factory;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;


//TODO fix local mappings
//TODO possibly use maven for mappings, can fix above at the same time
public class MappingsProvider extends DependencyProvider {
	public MinecraftMappedProvider mappedProvider;

	public String mappingsName;
	public String minecraftVersion;
	public String mappingsVersion;

	private boolean isV2;
	public File mappingsDir;
	private File unproposedTinyMappings;
	public File tinyMappings;
	public File mappingsMixinExport;

	public void clean(){
		tinyMappings.delete();
		unproposedTinyMappings.delete();
	}

	public Mappings getMappings() throws IOException {
		return MappingsCache.INSTANCE.get(tinyMappings.toPath());
	}

	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProvider minecraftProvider = getDependencyManager().getProvider(MinecraftProvider.class);

		project.getLogger().lifecycle(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		List<File> mappingsJars = new ArrayList<>(dependency.resolve());
		if(mappingsJars.isEmpty()) throw new RuntimeException("Could not find dependency " + dependency);


		this.mappingsName = dependency.getDependency().getGroup() + "." + dependency.getDependency().getName();

		Version mappingsVersion = new Version(version);
		this.minecraftVersion = mappingsVersion.getMinecraftVersion();
		this.mappingsVersion = mappingsVersion.getMappingsVersion();

		initFiles(project);

		if (!mappingsDir.exists()) {
			mappingsDir.mkdir();
		}

		if (!unproposedTinyMappings.exists() || !tinyMappings.exists()) {
			if (!unproposedTinyMappings.exists()) {
				saveMappings(project, mappingsJars);
			}
			project.getLogger().lifecycle(":populating field names");
			suggestFieldNames(minecraftProvider,unproposedTinyMappings.toPath(),tinyMappings.toPath());
		}

		mappedProvider = new MinecraftMappedProvider();
		mappedProvider.initFiles(project, minecraftProvider, this);
		mappedProvider.provide(dependency, project, extension, postPopulationScheduler);
	}

	private void saveMappings(Project project, List<File> mappingsJars) throws IOException {

		if(mappingsJars.size() == 1){
			// These are merged v1 mappings
			saveMergedV1Mappings(project, mappingsJars.get(0).toPath());
		}else if(mappingsJars.size() == 3){
			// These are unmerged v1 mappings or v2 mappings
			File unmergedYarn = mappingsJars.stream()
					.filter(f -> f.getName().startsWith("yarn-unmerged")).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find unmerged yarn mappings. Mappings: " + mappingsJars ));
			this.isV2 = FilenameUtils.removeExtension(unmergedYarn.getName()).endsWith("-v2");
			File unmergedIntermediary = mappingsJars.stream()
					.filter( f -> {
						String name = FilenameUtils.removeExtension(f.getName());
						boolean isV2Intermediary = name.endsWith("-v2");
						return name.startsWith("intermediary") && isV2 == isV2Intermediary;
					}).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find unmerged intermediary mappings. Mappings: " + mappingsJars));


			mergeAndSaveMappings(project, unmergedIntermediary.toPath(), unmergedYarn.toPath());
		}else {
			throw new RuntimeException("Found an unexpected amount of mapping jars: " + mappingsJars.size());
		}


		if (tinyMappings.exists()) {
			tinyMappings.delete();
		}



	}

	private void saveMergedV1Mappings(Project project, Path mappingsJar) throws IOException {
		project.getLogger().lifecycle(":extracting " + mappingsJar.getFileName());
		try (FileSystem fileSystem = FileSystems.newFileSystem(mappingsJar, null)) {
			extractMappings(fileSystem,unproposedTinyMappings.toPath());
		}
	}

	private void extractMappings(FileSystem jar, Path extractTo) throws IOException{
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}

	private void mergeAndSaveMappings(Project project, Path unmergedIntermediaryJar, Path unmergedYarnJar) throws IOException{
		Path unmergedIntermediary = File.createTempFile("unmerged_intermediary",".tiny").toPath();
		project.getLogger().lifecycle(":extracting " +unmergedIntermediaryJar.getFileName().toString());
		try(FileSystem unmergedIntermediaryFs = FileSystems.newFileSystem(unmergedIntermediaryJar, null)){
			extractMappings(unmergedIntermediaryFs,unmergedIntermediary);
		}

		Path unmergedYarn = File.createTempFile("unmerged_yarn",".tiny").toPath();
		project.getLogger().lifecycle(":extracting " +unmergedYarnJar.getFileName().toString());
		try(FileSystem unmergedYarnJarFs = FileSystems.newFileSystem(unmergedYarnJar, null)){
			extractMappings(unmergedYarnJarFs,unmergedYarn);
		}

		Path invertedIntermediary = File.createTempFile("inverted_intermediary",".tiny").toPath();
		reorderMappings(unmergedIntermediary,invertedIntermediary,"intermediary","official");
		Path unorderedMappings = File.createTempFile("unordered_merged",".tiny").toPath();
		project.getLogger().lifecycle(":merging");
		mergeMappings(invertedIntermediary,unmergedYarn,unorderedMappings);
		reorderMappings(unorderedMappings,unproposedTinyMappings.toPath(),"official","intermediary","named");
	}

	private void reorderMappings(Path oldMappings, Path newMappings, String... newOrder) {
		Command command = isV2 ? new CommandReorderTinyV2() : new CommandReorderTiny();
		String[] args = new String[2 + newOrder.length];
		args[0] = oldMappings.toAbsolutePath().toString();
		args[1] = newMappings.toAbsolutePath().toString();
		System.arraycopy(newOrder,0,args,2,newOrder.length);
		try {
			command.run(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void mergeMappings(Path intermediaryMappings, Path yarnMappings, Path newMergedMappings) {
		Command command = isV2 ? new CommandMergeTinyV2() : new CommandMergeTiny();
		try {
			command.run(new String[]{
				intermediaryMappings.toAbsolutePath().toString(),
					yarnMappings.toAbsolutePath().toString(),
					newMergedMappings.toAbsolutePath().toString(),
					"intermediary","official"
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void suggestFieldNames(MinecraftProvider minecraftProvider, Path oldMappings, Path newMappings){
		Command command = isV2? new CommandProposeV2FieldNames() : new CommandProposeFieldNames();
		try {
			command.run(new String[] {
						minecraftProvider.MINECRAFT_MERGED_JAR.getAbsolutePath(),
						oldMappings.toAbsolutePath().toString(),
						newMappings.toAbsolutePath().toString()
				});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	public void initFiles(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		mappingsDir = new File(extension.getUserCache(), "mappings");

		unproposedTinyMappings = new File(mappingsDir, mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion + "-base");
		tinyMappings = new File(mappingsDir, mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion);
		mappingsMixinExport = new File(extension.getProjectBuildCache(), "mixin-map-" + minecraftVersion + "-" + mappingsVersion + ".tiny");
	}

	@Override
	public String getTargetConfig() {
		return Constants.MAPPINGS;
	}
}
