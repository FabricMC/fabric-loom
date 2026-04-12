/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.extras.annotations.validate;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureVisitor;

import net.fabricmc.loom.util.Constants;

public final class TypePathCheckerVisitor extends SignatureVisitor {
	private final TypePath path;
	private final int pathLen;
	private int pathIndex = 0;
	private boolean reached = false;
	private String error = null;

	private final Deque<Integer> argIndexStack = new ArrayDeque<>();
	private final SignatureVisitor sink = new SignatureVisitor(Constants.ASM_VERSION) {
	};

	public TypePathCheckerVisitor(final TypePath path) {
		super(Constants.ASM_VERSION);
		this.path = path;
		this.pathLen = path == null ? 0 : path.getLength();

		if (this.pathLen == 0) {
			reached = true;
		}
	}

	private boolean hasMoreSteps() {
		return pathIndex < pathLen;
	}

	private int nextStepKind() {
		return path.getStep(pathIndex);
	}

	private int nextStepArgumentIndex() {
		return path.getStepArgument(pathIndex);
	}

	private String stepRepr(int i) {
		return switch (path.getStep(i)) {
		case TypePath.ARRAY_ELEMENT -> "[";
		case TypePath.INNER_TYPE -> ".";
		case TypePath.WILDCARD_BOUND -> "*";
		case TypePath.TYPE_ARGUMENT -> path.getStepArgument(i) + ";";
		default -> throw new AssertionError("Unexpected type path step kind: " + path.getStep(i));
		};
	}

	private String remainingSteps() {
		if (path == null || !hasMoreSteps()) {
			return "<none>";
		}

		StringBuilder sb = new StringBuilder();

		for (int i = pathIndex; i < pathLen; i++) {
			sb.append(stepRepr(i));
		}

		return sb.toString();
	}

	private void consumeStep() {
		pathIndex++;

		if (pathIndex == pathLen) {
			reached = true;
		}
	}

	@Nullable
	public String getError() {
		if (error != null) {
			return error;
		}

		if (!reached) {
			return "TypePath not fully consumed at index " + pathIndex + ", remaining steps: '" + remainingSteps() + "'";
		}

		return null;
	}

	@Override
	public void visitBaseType(final char descriptor) {
		if (hasMoreSteps()) {
			error = "TypePath has extra steps starting at index " + pathIndex + " ('" + remainingSteps()
					+ "') but reached base type descriptor '" + descriptor + "'.";
		} else {
			reached = true;
		}
	}

	@Override
	public void visitTypeVariable(final String name) {
		if (hasMoreSteps()) {
			error = "TypePath has extra steps starting at index " + pathIndex + " ('" + remainingSteps()
					+ "') but reached type variable '" + name + "'.";
		} else {
			reached = true;
		}
	}

	@Override
	public SignatureVisitor visitArrayType() {
		if (hasMoreSteps()) {
			if (nextStepKind() == TypePath.ARRAY_ELEMENT) {
				consumeStep();
			} else {
				error = "At step " + pathIndex + " expected array element '[' but found '" + stepRepr(pathIndex) + "'.";
			}
		}

		return this;
	}

	@Override
	public void visitClassType(final String name) {
		argIndexStack.push(0);

		String[] innerParts = name.split("\\$");

		for (int i = 1; i < innerParts.length; i++) {
			visitInnerClassType(innerParts[i]);
		}
	}

	@Override
	public void visitInnerClassType(final String name) {
		if (hasMoreSteps()) {
			if (nextStepKind() == TypePath.INNER_TYPE) {
				consumeStep();
			} else {
				error = "At step " + pathIndex + " expected inner type '.' but found '" + stepRepr(pathIndex) + "'.";
			}
		}

		argIndexStack.push(0);
	}

	@Override
	public void visitTypeArgument() {
		// unbounded wildcard '*' — terminal
		if (error != null) {
			return;
		}

		if (argIndexStack.isEmpty()) {
			error = "Type signature has no enclosing class type for an unbounded wildcard at step " + pathIndex + ".";
			return;
		}

		int idx = argIndexStack.pop();
		boolean targeted = hasMoreSteps() && nextStepKind() == TypePath.TYPE_ARGUMENT && nextStepArgumentIndex() == idx;

		if (targeted) {
			consumeStep();

			if (hasMoreSteps()) {
				error = "TypePath targets unbounded wildcard '*' at step " + (pathIndex - 1)
						+ " but contains further steps; '*' is terminal.";
			} else {
				reached = true;
			}
		}

		argIndexStack.push(idx + 1);
		// no nested visits for unbounded wildcard
	}

	@Override
	public SignatureVisitor visitTypeArgument(final char wildcard) {
		if (error != null) {
			return sink;
		}

		if (argIndexStack.isEmpty()) {
			error = "Type signature has no enclosing class type for a type argument at step " + pathIndex + ".";
			return sink;
		}

		int idx = argIndexStack.pop();
		boolean targeted = hasMoreSteps() && nextStepKind() == TypePath.TYPE_ARGUMENT && nextStepArgumentIndex() == idx;

		if (targeted) {
			consumeStep();

			if (pathIndex == pathLen) {
				// Path targets the whole type argument; success if no further navigation needed.
				argIndexStack.push(idx + 1);
				return sink;
			}
		}

		argIndexStack.push(idx + 1);

		if (hasMoreSteps() && nextStepKind() == TypePath.WILDCARD_BOUND) {
			if (wildcard == SignatureVisitor.EXTENDS || wildcard == SignatureVisitor.SUPER) {
				consumeStep();
			} else {
				error = "At step " + pathIndex + " found wildcard bound '*' but the type argument is exact ('=').";
			}
		}

		return this;
	}

	@Override
	public void visitEnd() {
		if (argIndexStack.isEmpty()) {
			if (error == null) {
				error = "visitEnd encountered with no matching class/inner-class at step " + pathIndex + ".";
			}
		} else {
			argIndexStack.pop();
		}
	}
}
