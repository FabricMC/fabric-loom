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

package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public final class SymlinkWalker {
	private static final ArrayList<String> pathToCheck = new ArrayList<>(8);

	private SymlinkWalker() { }

	public static boolean isPathSymbolic(Path path) throws IOException {
		path = path.toAbsolutePath();

		try {
			for (int i = 0; i < path.getNameCount(); i++) {
				pathToCheck.add(path.getName(i).toString());

				if (Files.isSymbolicLink(Paths.get(path.getRoot().toString(), pathToCheck.toArray(new String[0])))) {
					pathToCheck.clear();
					return true;
				}
			}
		} finally {
			pathToCheck.clear();
		}

		return false;
	}

	public static Path getRealPath(Path path) throws IOException {
		path = path.toAbsolutePath();
		Path RealPath = path.toAbsolutePath().getRoot();

		try {
			for (int i = 0; i < path.getNameCount(); i++) {
				pathToCheck.add(path.getName(i).toString());

				if (Files.isSymbolicLink(Paths.get(path.getRoot().toString(), pathToCheck.toArray(new String[0])))) {
					RealPath = RealPath.resolve(Paths
						.get(
							new File(
								Files.readSymbolicLink(
								Paths.get(
									path.getRoot().toString(),
									pathToCheck.toArray(new String[0])
								)
							)
							.toString()
						)
						.getCanonicalPath()
						)
						.toAbsolutePath()
					);
				} else {
					// If not symbolic link/doesn't exist then add in path relative.
					RealPath = RealPath.resolve(path.getName(i));
				}
			}
		} finally {
			pathToCheck.clear();
		}

		return RealPath;
	}
}
