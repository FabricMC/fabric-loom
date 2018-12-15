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

package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedDependency;

import java.io.File;
import java.util.Set;

public abstract class DependencyProvider {

	private LoomDependencyManager dependencyManager;

	public abstract void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension) throws Exception;

	public abstract String getTargetConfig();

	public void addDependency(Object object, Project project) {
		addDependency(object, project, "compile");
	}

	public void addDependency(Object object, Project project, String target) {
		if(object instanceof File){
			object = project.files(object);
		}
		project.getDependencies().add(target, object);
	}

	public void register(LoomDependencyManager dependencyManager){
		this.dependencyManager = dependencyManager;
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public static class DependencyInfo {
		final Dependency dependency;
		final Configuration sourceConfiguration;

		public DependencyInfo(Dependency dependency, Configuration sourceConfiguration) {
			this.dependency = dependency;
			this.sourceConfiguration = sourceConfiguration;
		}

		public Dependency getDependency() {
			return dependency;
		}

		public String getResolvedVersion() {
			for (ResolvedDependency rd : sourceConfiguration.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
				if (rd.getModuleGroup().equals(dependency.getGroup()) && rd.getModuleName().equals(dependency.getName())) {
					return rd.getModuleVersion();
				}
			}

			return dependency.getVersion();
		}

		public Configuration getSourceConfiguration() {
			return sourceConfiguration;
		}

		public Set<File> resolve(){
			return sourceConfiguration.files(dependency);
		}

		public File resolveFile(){
			Set<File> files = resolve();
			if(files.size() != 1){
				throw new RuntimeException(dependency + " resolves to more than one file");
			}
			File file = files.stream().findFirst().orElse(null);
			return file;
		}

		public String getDepString(){
			return dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
		}
	}
}
