/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2023 FabricMC
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

package net.fabricmc.loom.configuration.fabricapi;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.model.ObjectFactory;

import net.fabricmc.loom.api.fabricapi.DataGenerationSettings;
import net.fabricmc.loom.api.fabricapi.FabricApiExtension;

public abstract class FabricApiExtensionImpl implements FabricApiExtension {
	@Inject
	protected abstract ObjectFactory getObjectFactory();

	private final FabricApiVersions versions;
	private final FabricApiDataGeneration dataGeneration;

	public FabricApiExtensionImpl() {
		versions = getObjectFactory().newInstance(FabricApiVersions.class);
		dataGeneration = getObjectFactory().newInstance(FabricApiDataGeneration.class);
	}

	@Override
	public Dependency module(String moduleName, String fabricApiVersion) {
		return versions.module(moduleName, fabricApiVersion);
	}

	@Override
	public String moduleVersion(String moduleName, String fabricApiVersion) {
		return versions.moduleVersion(moduleName, fabricApiVersion);
	}

	@Override
	public void configureDataGeneration() {
		configureDataGeneration(dataGenerationSettings -> { });
	}

	@Override
	public void configureDataGeneration(Action<DataGenerationSettings> action) {
		dataGeneration.configureDataGeneration(action);
	}
}
