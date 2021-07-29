/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2021 FabricMC
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
import org.jetbrains.annotations.Nullable;

public record RemappedConfigurationEntry(String sourceConfiguration, String targetConfiguration, boolean compileClasspath, boolean runtimeClasspath, String consumerConfiguration, @Nullable String replacedWith) {
	public RemappedConfigurationEntry(String sourceConfiguration, String targetConfiguration, boolean compileClasspath, boolean runtimeClasspath, String consumerConfiguration) {
		this(sourceConfiguration, targetConfiguration, compileClasspath, runtimeClasspath, consumerConfiguration, null);
	}

	public boolean hasConsumerConfiguration() {
		return consumerConfiguration != null && !consumerConfiguration.isEmpty();
	}

	public String getRemappedConfiguration() {
		return sourceConfiguration + "Mapped";
	}

	public String getTargetConfiguration(ConfigurationContainer container) {
		if (container.findByName(targetConfiguration) == null) {
			return JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;
		}

		return targetConfiguration;
	}

	// TODO: Remove this in Loom 0.12
	@Nullable
	public String mavenScope() {
		if (hasConsumerConfiguration()) {
			return switch (consumerConfiguration) {
			case JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME -> "compile";
			case JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME -> "runtime";
			default -> null;
			};
		}

		return null;
	}
}
