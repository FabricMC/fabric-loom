/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.configuration.ide;

import java.util.Locale;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Platform;
import net.fabricmc.loom.util.Strings;
import net.fabricmc.loom.util.gradle.GradleUtils;

public class DefaultRunConfigurationSettings {
	// Configure the default values before the user can modify them.
	public static void configureDefaults(RunConfiguration run, Project project) {
		run.getAppendProjectPathToDisplayName().convention(true);
		run.getMainClass().convention(run.getRuntimeEnvironment().map(side -> RunConfigUtils.getMainClass(side, LoomGradleExtension.get(project))));
		run.getDevLaunchMainClass().convention(Constants.DLI_ENTRYPOINT);
		run.getSourceSet().convention(run.getRuntimeEnvironment().map(runtimeEnvironment -> MinecraftSourceSets.get(project).getSourceSetForEnv(runtimeEnvironment)));
		run.getDisplayName().convention(run.getSourceSet().map(sourceSet -> {
			String configName = "";

			final boolean isSplitClientSourceSet = LoomGradleExtension.get(project).areEnvironmentSourceSetsSplit()
					&& sourceSet.equals("client")
					&& run.getRuntimeEnvironment().get().equals("client");

			if (!sourceSet.equals(SourceSet.MAIN_SOURCE_SET_NAME) && !isSplitClientSourceSet) {
				configName += Strings.capitalizeCamelCaseName(sourceSet) + " ";
			}

			configName += "Minecraft " + Strings.capitalizeCamelCaseName(run.getName());
			return configName;
		}));
		run.getRunDirectory().set(project.file("run"));
		run.getGenerateRunConfig().convention(GradleUtils.isRootProject(project));
		run.getPreferGradleTask().convention(false);
	}

	// Apply any additional configuration after the user has modified the settings, but before the run config is generated.
	public static RunConfiguration finialise(RunConfiguration run, Project project) {
		RunConfigurationInternal internalRun = (RunConfigurationInternal) run;

		if (internalRun.getIsFinalised().getOrElse(false)) {
			return RunConfigUtils.toSerialisable(run, project);
		}

		internalRun.getIsFinalised().set(true);
		internalRun.getIsFinalised().finalizeValue();

		LoomGradleExtension extension = LoomGradleExtension.get(project);
		LibraryContext context = new LibraryContext(extension.getMinecraftProvider().getVersionInfo(), JavaVersion.current());
		MinecraftVersionMeta.JavaVersion javaVersion = extension.getMinecraftProvider().getVersionInfo().javaVersion();

		String environment = run.getRuntimeEnvironment().get().toLowerCase(Locale.ROOT);

		run.getJvmArguments().add("-Dfabric.dli.config=" + encodeEscaped(extension.getFiles().getDevLauncherConfig().getAbsolutePath()));
		run.getJvmArguments().add("-Dfabric.dli.env=" + environment);
		run.getJvmArguments().add("-Dfabric.dli.main=" + run.getMainClass().get());

		if (environment.equals("client")) {
			if (context.usesLWJGL3() && Platform.CURRENT.getOperatingSystem().isMacOS()) {
				run.getJvmArguments().add("-XstartOnFirstThread");
			}

			if (Platform.CURRENT.isRaspberryPi()) {
				run.getEnvironmentVars().put("MESA_GL_VERSION_OVERRIDE", "4.3");
			}
		}

		if (javaVersion != null && javaVersion.majorVersion() >= 25) {
			run.getJvmArguments().add("--sun-misc-unsafe-memory-access=allow");
			run.getJvmArguments().add("--enable-native-access=ALL-UNNAMED");
		}

		run.getSystemProperties().get().forEach((key, value) -> {
			if (value.isBlank()) {
				run.getJvmArguments().add("-D%s".formatted(key));
			} else {
				run.getJvmArguments().add("-D%s=%s".formatted(key, value));
			}
		});

		finialiseValues(run);

		return RunConfigUtils.toSerialisable(run, project);
	}

	static void finialiseValues(RunConfiguration run) {
		run.getRuntimeEnvironment().finalizeValue();
		run.getSystemProperties().finalizeValue();
		run.getDisplayName().finalizeValue();
		run.getJvmArguments().finalizeValue();
		run.getProgramArguments().finalizeValue();
		run.getEnvironmentVars().finalizeValue();
		run.getAppendProjectPathToDisplayName().finalizeValue();
		run.getMainClass().finalizeValue();
		run.getSourceSet().finalizeValue();
		run.getRunDirectory().finalizeValue();
		run.getGenerateRunConfig().finalizeValue();
		run.getPreferGradleTask().finalizeValue();
		run.getIdeConfigFolder().finalizeValue();
		run.getDevLaunchMainClass().finalizeValue();
	}

	private static String encodeEscaped(String s) {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
				ret.append(String.format(Locale.ENGLISH, "@@%04x", (int) c));
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}
}
