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

package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

/**
 * Contains shortcuts to create tiny remappers using the mappings accessibly to the project.
 */
public final class TinyRemapperHelper {
	private static final Map<String, String> JSR_TO_JETBRAINS = new ImmutableMap.Builder<String, String>()
			.put("javax/annotation/Nullable", "org/jetbrains/annotations/Nullable")
			.put("javax/annotation/Nonnull", "org/jetbrains/annotations/NotNull")
			.put("javax/annotation/concurrent/Immutable", "org/jetbrains/annotations/Unmodifiable")
			.build();

	private TinyRemapperHelper() {
	}

	public static TinyRemapper getTinyRemapper(Project project, String fromM, String toM) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		return TinyRemapper.newRemapper()
				.withMappings(create(extension.getMappingsProvider().getMappings(), fromM, toM, true))
				.withMappings(out -> JSR_TO_JETBRAINS.forEach(out::acceptClass))
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.build();
	}

	public static Path[] getMinecraftDependencies(Project project) {
		return project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES).getFiles()
				.stream().map(File::toPath).toArray(Path[]::new);
	}

	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}

	public static IMappingProvider create(MappingTree mappings, String from, String to, boolean remapLocalVariables) {
		return (acceptor) -> {
			for (MappingTree.ClassMapping classDef : mappings.getClasses()) {
				String className = classDef.getName(from);
				acceptor.acceptClass(className, classDef.getName(to));

				for (MappingTree.FieldMapping field : classDef.getFields()) {
					acceptor.acceptField(memberOf(className, field.getName(from), field.getDesc(from)), field.getName(to));
				}

				for (MappingTree.MethodMapping method : classDef.getMethods()) {
					IMappingProvider.Member methodIdentifier = memberOf(className, method.getName(from), method.getDesc(from));
					acceptor.acceptMethod(methodIdentifier, method.getName(to));

					if (remapLocalVariables) {
						for (MappingTree.MethodArgMapping parameter : method.getArgs()) {
							String name = parameter.getName(to);

							if (name == null) {
								continue;
							}

							acceptor.acceptMethodArg(methodIdentifier, parameter.getLvIndex(), name);
						}

						for (MappingTree.MethodVarMapping localVariable : method.getVars()) {
							acceptor.acceptMethodVar(methodIdentifier, localVariable.getLvIndex(),
									localVariable.getStartOpIdx(), localVariable.getLvtRowIndex(),
									localVariable.getName(to));
						}
					}
				}
			}
		};
	}
}
