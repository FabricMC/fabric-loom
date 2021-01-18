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

package net.fabricmc.loom.configuration;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleSupport;

public class RemappedConfigurationEntry {
	private final String sourceConfiguration;
	private final String targetConfiguration;
	private final String mavenScope;
	private final boolean isOnModCompileClasspath;

	public RemappedConfigurationEntry(String sourceConfiguration, String targetConfiguration, boolean isOnModCompileClasspath, String mavenScope) {
		this.sourceConfiguration = sourceConfiguration;
		this.targetConfiguration = targetConfiguration;
		this.isOnModCompileClasspath = isOnModCompileClasspath;
		this.mavenScope = mavenScope;
	}

	public String getMavenScope() {
		return mavenScope;
	}

	public boolean hasMavenScope() {
		return mavenScope != null && !mavenScope.isEmpty();
	}

	public boolean isOnModCompileClasspath() {
		return isOnModCompileClasspath;
	}

	public String getSourceConfiguration() {
		return sourceConfiguration;
	}

	public String getRemappedConfiguration() {
		return sourceConfiguration + "Mapped";
	}

	public String getTargetConfiguration(ConfigurationContainer container) {
		if (container.findByName(targetConfiguration) == null) {
			return GradleSupport.IS_GRADLE_7_OR_NEWER ? JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME : Constants.Configurations.COMPILE;
		}

		return targetConfiguration;
	}
}
