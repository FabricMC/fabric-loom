/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.api.mappings.layered.spec;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.util.ConfigureUtil;
import org.jetbrains.annotations.ApiStatus;

/**
 * Used to configure a layered mapping spec.
 */
@ApiStatus.Experimental
public interface LayeredMappingSpecBuilder {
	/**
	 * Add a MappingsSpec layer.
	 */
	LayeredMappingSpecBuilder addLayer(MappingsSpec<?> mappingSpec);

	/**
	 * Add a layer that uses the official mappings provided by Mojang with the default options.
	 */
	default LayeredMappingSpecBuilder officialMojangMappings() {
		return officialMojangMappings(builder -> { });
	}

	/**
	 * Configure and add a layer that uses the official mappings provided by Mojang.
	 */
	@SuppressWarnings("rawtypes")
	default LayeredMappingSpecBuilder officialMojangMappings(@DelegatesTo(value = MojangMappingsSpecBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
		return officialMojangMappings(mojangMappingsSpecBuilder -> ConfigureUtil.configure(closure, mojangMappingsSpecBuilder));
	}

	/**
	 * Configure and add a layer that uses the official mappings provided by Mojang.
	 */
	LayeredMappingSpecBuilder officialMojangMappings(Action<MojangMappingsSpecBuilder> action);

	default LayeredMappingSpecBuilder parchment(Object object) {
		return parchment(object, parchmentMappingsSpecBuilder -> parchmentMappingsSpecBuilder.setRemovePrefix(true));
	}

	@SuppressWarnings("rawtypes")
	default LayeredMappingSpecBuilder parchment(Object object, @DelegatesTo(value = ParchmentMappingsSpecBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
		return parchment(object, parchmentMappingsSpecBuilder -> ConfigureUtil.configure(closure, parchmentMappingsSpecBuilder));
	}

	LayeredMappingSpecBuilder parchment(Object object, Action<ParchmentMappingsSpecBuilder> action);

	/**
	 * Add a signatureFix layer. Reads the @extras/record_signatures.json" file in a jar file such as yarn.
	 */
	@ApiStatus.Experimental
	LayeredMappingSpecBuilder signatureFix(Object object);
}
