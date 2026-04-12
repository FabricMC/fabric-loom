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

package net.fabricmc.loom.util;

import java.io.OutputStream;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

public abstract class XVFBExistsValueSource implements ValueSource<Boolean, ValueSourceParameters.None> {
	public static final String XVFB = "xvfb-run";

	@Inject
	protected abstract ExecOperations getExecOperations();

	@Override
	public Boolean obtain() {
		ExecResult result = getExecOperations().exec(spec -> {
			spec.commandLine(XVFB);
			spec.args("--help");

			// We don't care about the output
			spec.setStandardOutput(OutputStream.nullOutputStream());
			spec.setErrorOutput(OutputStream.nullOutputStream());
		});

		return result.getExitValue() == 0;
	}

	public static Provider<Boolean> exists(Project project) {
		return project.getProviders().of(XVFBExistsValueSource.class, i -> { });
	}
}
