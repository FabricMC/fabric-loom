/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2024 FabricMC
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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.task.service.MigrateMappingsService;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

@UntrackedTask(because = "Always rerun this task.")
public abstract class MigrateMappingsTask extends AbstractLoomTask {
	@Input
	@Option(option = "mappings", description = "Target mappings")
	public abstract Property<String> getMappings();

	@InputDirectory
	@Option(option = "input", description = "Java source file directory")
	public abstract DirectoryProperty getInputDir();

	@InputFiles
	public abstract ConfigurableFileCollection getInputClasspath();

	@OutputDirectory
	@Option(option = "output", description = "Remapped source output directory")
	public abstract DirectoryProperty getOutputDir();

	@Nested
	protected abstract Property<MigrateMappingsService.Options> getMigrationServiceOptions();

	public MigrateMappingsTask() {
		getInputDir().convention(getProject().getLayout().getProjectDirectory().dir("src/main/java"));
		getOutputDir().convention(getProject().getLayout().getProjectDirectory().dir("remappedSrc"));

		getInputClasspath().from(getProject().getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).map(AbstractArchiveTask::getArchiveFile));

		getMigrationServiceOptions().set(MigrateMappingsService.createOptions(getProject(), getMappings(), getInputClasspath(), getInputDir(), getOutputDir()));
	}

	@TaskAction
	public void doTask() throws Throwable {
		try (var serviceFactory = new ScopedServiceFactory()) {
			MigrateMappingsService service = serviceFactory.get(getMigrationServiceOptions().get());
			service.migrateMapppings();
		}
	}
}
