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

package net.fabricmc.loom.decompilers.cfr;

import java.util.Arrays;
import java.util.Collection;

import org.benf.cfr.reader.util.output.Dumper;

import net.fabricmc.loom.api.decompilers.JavadocStyle;

public abstract class DocPrinter {
	protected final Dumper dumper;

	public DocPrinter(Dumper dumper) {
		this.dumper = dumper;
	}

	public static DocPrinter of(Dumper dumper, JavadocStyle style) {
		return switch (style) {
		case HTML -> new HtmlPrinter(dumper);
		case MARKDOWN -> new MarkdownPrinter(dumper);
		};
	}

	public abstract void printHeader();
	public abstract void printLine(String line);
	public abstract void printFooter();

	public void printEmptyLine() {
		printLine("");
	}

	public void printComment(String comment) {
		if (comment != null && !comment.isBlank()) {
			printComment(comment.split("\\R"));
		}
	}

	public void printComment(String[] lines) {
		printComment(Arrays.asList(lines));
	}

	public void printComment(Collection<String> lines) {
		printHeader();

		for (String line : lines) {
			printLine(line);
		}

		printFooter();
	}

	private static final class HtmlPrinter extends DocPrinter {
		HtmlPrinter(Dumper dumper) {
			super(dumper);
		}

		@Override
		public void printHeader() {
			dumper.print("/**").newln();
		}

		@Override
		public void printLine(String line) {
			if (line.isEmpty()) {
				// no trailing whitespace
				dumper.print(" *").newln();
			} else {
				dumper.print(" * ").print(line).newln();
			}
		}

		@Override
		public void printFooter() {
			dumper.print(" */").newln();
		}
	}

	private static final class MarkdownPrinter extends DocPrinter {
		MarkdownPrinter(Dumper dumper) {
			super(dumper);
		}

		@Override
		public void printHeader() {
		}

		@Override
		public void printLine(String line) {
			if (line.isEmpty()) {
				// no trailing whitespace
				dumper.print("///").newln();
			} else {
				dumper.print("/// ").print(line).newln();
			}
		}

		@Override
		public void printFooter() {
		}
	}
}
