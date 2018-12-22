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

package net.fabricmc.loom.task;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.HashMap;
import java.util.Map;

public class LineNumberAdjustmentVisitor extends ClassVisitor {
	public class Method extends MethodVisitor {
		public Method(int api, MethodVisitor methodVisitor) {
			super(api, methodVisitor);
		}

		@Override
		public void visitLineNumber(final int line, final Label start) {
			int tLine = line;
			if (tLine <= 0) {
				super.visitLineNumber(1, start);
			} else if (tLine >= maxLine) {
				super.visitLineNumber(maxLineDst, start);
			} else {
				Integer matchedLine = null;

				while (tLine <= maxLine && ((matchedLine = lineNumberMap.get(tLine)) == null)) {
					tLine++;
				}

				super.visitLineNumber(matchedLine != null ? matchedLine : maxLineDst, start);
			}
		}
	}

	private final Map<Integer, Integer> lineNumberMap;
	private int maxLine, maxLineDst;

	public LineNumberAdjustmentVisitor(int api, ClassVisitor classVisitor, int[] mapping) {
		super(api, classVisitor);

		lineNumberMap = new HashMap<>();
		maxLine = 0;

		for (int i = 0; i < mapping.length; i += 2) {
			lineNumberMap.put(mapping[i], mapping[i+1]);
			if (mapping[i] > maxLine) {
				maxLine = mapping[i];
			}
			if (mapping[i+1] > maxLineDst) {
				maxLineDst = mapping[i+1];
			}
		}
	}

	@Override
	public MethodVisitor visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions) {
		return new Method(api, super.visitMethod(access, name, descriptor, signature, exceptions));
	}
}
