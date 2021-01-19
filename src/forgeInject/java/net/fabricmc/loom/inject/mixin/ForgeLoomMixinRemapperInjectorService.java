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

package net.fabricmc.loom.inject.mixin;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class ForgeLoomMixinRemapperInjectorService implements ITransformationService {
	private static final Logger LOGGER = LogManager.getLogger("ForgeLoomRemapperInjector");

	@Override
	public String name() {
		return "ForgeLoomMixinRemapperInjector";
	}

	@Override
	public void initialize(IEnvironment environment) {
	}

	@Override
	public void beginScanning(IEnvironment environment) {
		LOGGER.debug("We will be injecting our remapper.");

		try {
			MixinEnvironment.getDefaultEnvironment().getRemappers().add(new MixinIntermediaryDevRemapper(Objects.requireNonNull(resolveMappings()), "intermediary", "named"));
			LOGGER.debug("We have successfully injected our remapper.");
		} catch (Exception e) {
			LOGGER.debug("We have failed to inject our remapper.", e);
		}
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
	}

	@Override
	public List<ITransformer> transformers() {
		return Collections.emptyList();
	}

	private static TinyTree resolveMappings() {
		try {
			String srgNamedProperty = System.getProperty("mixin.forgeloom.inject.mappings.srg-named");
			Path path = Paths.get(srgNamedProperty);

			try (BufferedReader reader = Files.newBufferedReader(path)) {
				return TinyMappingFactory.loadWithDetection(reader);
			}
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			return null;
		}
	}
}
