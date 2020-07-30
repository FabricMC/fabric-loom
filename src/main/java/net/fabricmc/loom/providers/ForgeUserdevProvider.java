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

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class ForgeUserdevProvider extends DependencyProvider {
	public ForgeUserdevProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		Path configJson = getExtension()
				.getProjectPersistentCache()
				.toPath()
				.resolve("forge-config-" + dependency.getDependency().getVersion() + ".json");

		if (Files.notExists(configJson) || isRefreshDeps()) {
			File resolved = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve Forge userdev"));
			Files.copy(resolved.toPath(), configJson);
		}

		JsonObject json;

		try (Reader reader = Files.newBufferedReader(configJson)) {
			json = new Gson().fromJson(reader, JsonObject.class);
		}

		addDependency(json.get("mcp").getAsString(), Constants.MCP_CONFIG);
		addDependency(json.get("universal").getAsString(), Constants.FORGE_UNIVERSAL);

		for (JsonElement lib : json.get("libraries").getAsJsonArray()) {
			addDependency(lib.getAsString(), Constants.FORGE_DEPENDENCIES);
		}

		// TODO: Read launch configs from the JSON too
		// TODO: Should I copy the patches from here as well?
		//       That'd require me to run the "MCP environment" fully up to merging.
	}

	@Override
	public String getTargetConfig() {
		return Constants.FORGE_USERDEV;
	}
}
