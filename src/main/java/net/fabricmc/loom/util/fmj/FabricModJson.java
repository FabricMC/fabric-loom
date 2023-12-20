/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.util.fmj;

import static net.fabricmc.loom.util.fmj.FabricModJsonUtils.readString;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public abstract sealed class FabricModJson permits FabricModJsonV0, FabricModJsonV1, FabricModJsonV2, FabricModJson.Mockable {
	protected final JsonObject jsonObject;
	private final FabricModJsonSource source;

	protected FabricModJson(JsonObject jsonObject, FabricModJsonSource source) {
		this.jsonObject = Objects.requireNonNull(jsonObject);
		this.source = Objects.requireNonNull(source);
	}

	public abstract int getVersion();

	public String getId() {
		return readString(jsonObject, "id");
	}

	public String getModVersion() {
		return readString(jsonObject, "version");
	}

	@Nullable
	public abstract JsonElement getCustom(String key);

	public abstract List<String> getMixinConfigurations();

	public abstract Map<String, ModEnvironment> getClassTweakers();

	public final FabricModJsonSource getSource() {
		return source;
	}

	@Override
	public final String toString() {
		return getClass().getName() + "[id=%s, version=%s, classTweakers=%s]".formatted(getId(), getVersion(), getClassTweakers());
	}

	@Override
	public final int hashCode() {
		return Objects.hash(getId(), getVersion());
	}

	@VisibleForTesting
	public abstract non-sealed class Mockable extends FabricModJson {
		private Mockable() {
			super(null, null);
			throw new AssertionError();
		}
	}
}
