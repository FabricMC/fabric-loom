/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.providers.mappings.tiny;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

/**
 * Adapted from {@link net.fabricmc.mappingio.adapter.MappingNsCompleter}.
 * This visitor completes any empty namespace with some alternative namespace
 * only if that alternative namespace is equal to some test namespace.
 * Mostly this will be used to complete official namespaces with intermediary
 * names only if those intermediary names are equal to the named names.
 */
public final class UnobfuscatedMappingNsCompleter extends ForwardingMappingVisitor {
	private final String testNs;
	private final Map<String, String> alternatives;
	private int testNsId;
	private int[] alternativesMapping;

	private String srcName;
	private String[] dstNames;
	private boolean[] unobf;
	private boolean[] lastMethodUnobf;

	private boolean relayHeaderOrMetadata;

	public UnobfuscatedMappingNsCompleter(MappingVisitor next, String testNs, Map<String, String> alternatives) {
		super(next);

		this.testNs = testNs;
		this.alternatives = alternatives;
	}

	@Override
	public boolean visitHeader() throws IOException {
		relayHeaderOrMetadata = next.visitHeader();

		return true;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		int count = dstNamespaces.size();
		testNsId = -1;
		alternativesMapping = new int[count];
		dstNames = new String[count];
		unobf = new boolean[count + 1]; // contains src ns as well
		lastMethodUnobf = new boolean[count + 1]; // contains src ns as well

		for (int i = 0; i < count; i++) {
			String dst = dstNamespaces.get(i);

			if (testNs.equals(dst)) {
				testNsId = i;
			}

			String src = alternatives.get(dst);
			int srcIdx;

			if (src == null) {
				srcIdx = i;
			} else if (src.equals(srcNamespace)) {
				srcIdx = -1;
			} else {
				srcIdx = dstNamespaces.indexOf(src);
				if (srcIdx < 0) throw new RuntimeException("invalid alternative mapping ns "+src+": not in "+dstNamespaces+" or "+srcNamespace);
			}

			alternativesMapping[i] = srcIdx;
		}

		if (testNsId == -1 && !testNs.equals(srcNamespace)) throw new RuntimeException("test namespace " + testNs + " not present in src and dst namespaces!");

		if (relayHeaderOrMetadata) next.visitNamespaces(srcNamespace, dstNamespaces);
	}

	@Override
	public void visitMetadata(String key, @Nullable String value) throws IOException {
		if (relayHeaderOrMetadata) next.visitMetadata(key, value);
	}

	@Override
	public boolean visitContent() throws IOException {
		relayHeaderOrMetadata = true; // for in-content metadata

		return next.visitContent();
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitClass(srcName);
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		this.srcName = srcName;

		return next.visitField(srcName, srcDesc);
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		this.srcName = srcName;

		return next.visitMethod(srcName, srcDesc);
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitMethodArg(argPosition, lvIndex, srcName);
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, endOpIdx, srcName);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		dstNames[namespace] = name;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		for (int ns : alternativesMapping) {
			int idx = ns + 1; // offset by 1 bc src ns is -1

			if (targetKind == MappedElementKind.METHOD_ARG || targetKind == MappedElementKind.METHOD_VAR) {
				unobf[idx] = lastMethodUnobf[idx];
			} else if (ns == testNsId) {
				unobf[idx] = true;

				if (targetKind == MappedElementKind.METHOD) {
					lastMethodUnobf[idx] = true;
				}
			} else if (!unobf[idx]) { // only check each ns once
				String name = ns == -1 ? srcName : dstNames[ns];
				String testName = dstNames[testNsId];

				if (testName != null && testName.equals(name)) {
					unobf[idx] = true;

					if (targetKind == MappedElementKind.METHOD) {
						lastMethodUnobf[idx] = true;
					}
				}
			}
		}

		nsLoop: for (int i = 0; i < dstNames.length; i++) {
			String name = dstNames[i];

			if (name == null) {
				int src = i;
				long visited = 1L << src;

				do {
					int newSrc = alternativesMapping[src];

					if (newSrc < 0) { // mapping to src name
						if (unobf[newSrc + 1]) {
							name = srcName;
							break; // srcName must never be null
						} else {
							continue nsLoop;
						}
					} else if (newSrc == src) { // no-op (identity) mapping, explicit in case src > 64
						continue nsLoop; // always null
					} else if ((visited & 1L << newSrc) != 0) { // cyclic mapping
						continue nsLoop; // always null
					} else {
						if (unobf[newSrc + 1]) {
							src = newSrc;
							name = dstNames[src];
							visited |= 1L << src;
						} else {
							continue nsLoop;
						}
					}
				} while (name == null);

				assert name != null;
			}

			next.visitDstName(targetKind, i, name);
		}

		Arrays.fill(dstNames, null);
		Arrays.fill(unobf, false);
		Arrays.fill(lastMethodUnobf, false);

		return next.visitElementContent(targetKind);
	}
}
