/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.configuration.ifaceinject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.util.CheckSignatureAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.LazyCloseable;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrRemapper;

public abstract class InterfaceInjectionProcessor implements MinecraftJarProcessor<InterfaceInjectionProcessor.Spec> {
	private static final Logger LOGGER = LoggerFactory.getLogger(InterfaceInjectionProcessor.class);

	private final String name;
	private final boolean fromDependencies;

	@Inject
	public InterfaceInjectionProcessor(String name, boolean fromDependencies) {
		this.name = name;
		this.fromDependencies = fromDependencies;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public @Nullable InterfaceInjectionProcessor.Spec buildSpec(SpecContext context) {
		List<InjectedInterface> injectedInterfaces = new ArrayList<>();

		injectedInterfaces.addAll(InjectedInterface.fromMods(context.localMods()));
		// Find the injected interfaces from mods that are both on the compile and runtime classpath.
		// Runtime is also required to ensure that the interface and it's impl is present when running the mc jar.

		if (fromDependencies) {
			injectedInterfaces.addAll(InjectedInterface.fromMods(context.modDependenciesCompileRuntime()));
		}

		if (injectedInterfaces.isEmpty()) {
			return null;
		}

		return new Spec(injectedInterfaces);
	}

	public record Spec(List<InjectedInterface> injectedInterfaces) implements MinecraftJarProcessor.Spec {
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
		// Remap from intermediary->named
		final MemoryMappingTree mappings = context.getMappings();
		final int intermediaryIndex = mappings.getNamespaceId(MappingsNamespace.INTERMEDIARY.toString());
		final int namedIndex = mappings.getNamespaceId(MappingsNamespace.NAMED.toString());

		try (LazyCloseable<TinyRemapper> tinyRemapper = context.createRemapper(MappingsNamespace.INTERMEDIARY, MappingsNamespace.NAMED)) {
			final List<InjectedInterface> remappedInjectedInterfaces = spec.injectedInterfaces().stream()
					.map(injectedInterface -> remap(
							injectedInterface,
							s -> mappings.mapClassName(s, intermediaryIndex, namedIndex),
							tinyRemapper.get().getEnvironment().getRemapper()
					))
					.toList();
			try {
				ZipUtils.transform(jar, getTransformers(remappedInjectedInterfaces));
			} catch (IOException e) {
				throw new RuntimeException("Failed to apply interface injections to " + jar, e);
			}
		}
	}

	private InjectedInterface remap(InjectedInterface in, Function<String, String> remapper, TrRemapper signatureRemapper) {
		String generics = null;

		if (in.generics() != null) {
			String fakeSignature = signatureRemapper.mapSignature("Ljava/lang/Object" + in.generics() + ";", false); // Turning the raw generics string into a fake signature
			generics = fakeSignature.substring("Ljava/lang/Object".length(), fakeSignature.length() - 1); // Retrieving the remapped raw generics string from the remapped fake signature
		}

		return new InjectedInterface(
				in.modId(),
				remapper.apply(in.className()),
				remapper.apply(in.ifaceName()),
				generics
		);
	}

	private List<Pair<String, ZipUtils.UnsafeUnaryOperator<byte[]>>> getTransformers(List<InjectedInterface> injectedInterfaces) {
		return injectedInterfaces.stream()
				.collect(Collectors.groupingBy(InjectedInterface::className))
				.entrySet()
				.stream()
				.map(entry -> {
					final String zipEntry = entry.getKey().replaceAll("\\.", "/") + ".class";
					return new Pair<>(zipEntry, getTransformer(entry.getValue()));
				}).toList();
	}

	private ZipUtils.UnsafeUnaryOperator<byte[]> getTransformer(List<InjectedInterface> injectedInterfaces) {
		return input -> {
			final ClassReader reader = new ClassReader(input);
			final ClassWriter writer = new ClassWriter(0);
			final ClassVisitor classVisitor = new InjectingClassVisitor(Constants.ASM_VERSION, writer, injectedInterfaces);
			reader.accept(classVisitor, 0);
			return writer.toByteArray();
		};
	}

	@Override
	public MappingsProcessor<Spec> processMappings() {
		return (mappings, spec, context) -> {
			if (!MappingsNamespace.INTERMEDIARY.toString().equals(mappings.getSrcNamespace())) {
				throw new IllegalStateException("Mapping tree must have intermediary src mappings not " + mappings.getSrcNamespace());
			}

			Map<String, List<InjectedInterface>> map = spec.injectedInterfaces().stream()
					.collect(Collectors.groupingBy(InjectedInterface::className));

			for (Map.Entry<String, List<InjectedInterface>> entry : map.entrySet()) {
				final String className = entry.getKey();
				final List<InjectedInterface> injectedInterfaces = entry.getValue();

				MappingTree.ClassMapping classMapping = mappings.getClass(className);

				if (classMapping == null) {
					final String modIds = injectedInterfaces.stream().map(InjectedInterface::modId).distinct().collect(Collectors.joining(","));
					LOGGER.warn("Failed to find class ({}) to add injected interfaces from mod(s) ({})", className, modIds);
					continue;
				}

				classMapping.setComment(appendComment(classMapping.getComment(), injectedInterfaces));
			}

			return true;
		};
	}

	private static String appendComment(String comment, List<InjectedInterface> injectedInterfaces) {
		if (injectedInterfaces.isEmpty()) {
			return comment;
		}

		var commentBuilder = comment == null ? new StringBuilder() : new StringBuilder(comment);

		for (InjectedInterface injectedInterface : injectedInterfaces) {
			String iiComment = "<p>Interface {@link %s} injected by mod %s</p>".formatted(injectedInterface.ifaceName().replace('/', '.').replace('$', '.'), injectedInterface.modId());

			if (commentBuilder.indexOf(iiComment) == -1) {
				if (commentBuilder.isEmpty()) {
					commentBuilder.append(iiComment);
				} else {
					commentBuilder.append('\n').append(iiComment);
				}
			}
		}

		return comment;
	}

	private record InjectedInterface(String modId, String className, String ifaceName, @Nullable String generics) {
		public static List<InjectedInterface> fromMod(FabricModJson fabricModJson) {
			final String modId = fabricModJson.getId();
			final JsonElement jsonElement = fabricModJson.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE);

			if (jsonElement == null) {
				return Collections.emptyList();
			}

			final JsonObject addedIfaces = jsonElement.getAsJsonObject();

			final List<InjectedInterface> result = new ArrayList<>();

			for (String className : addedIfaces.keySet()) {
				final JsonArray ifacesInfo = addedIfaces.getAsJsonArray(className);

				for (JsonElement ifaceElement : ifacesInfo) {
					String ifaceInfo = ifaceElement.getAsString();

					String name = ifaceInfo;
					String generics = null;

					if (ifaceInfo.contains("<") && ifaceInfo.contains(">")) {
						name = ifaceInfo.substring(0, ifaceInfo.indexOf("<"));
						generics = ifaceInfo.substring(ifaceInfo.indexOf("<"));

						// First Generics Check, if there are generics, are them correctly written?
						SignatureReader reader = new SignatureReader("Ljava/lang/Object" + generics + ";");
						CheckSignatureAdapter checker = new CheckSignatureAdapter(CheckSignatureAdapter.CLASS_SIGNATURE, null);
						reader.accept(checker);
					}

					result.add(new InjectedInterface(modId, className, name, generics));
				}
			}

			return result;
		}

		public static List<InjectedInterface> fromMods(List<FabricModJson> fabricModJsons) {
			return fabricModJsons.stream()
					.map(InjectedInterface::fromMod)
					.flatMap(List::stream)
					.toList();
		}

		public static boolean containsGenerics(List<InjectedInterface> injectedInterfaces) {
			for (InjectedInterface injectedInterface : injectedInterfaces) {
				if (injectedInterface.generics() != null) {
					return true;
				}
			}

			return false;
		}
	}

	private static class InjectingClassVisitor extends ClassVisitor {
		private static final int INTERFACE_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE;

		private final List<InjectedInterface> injectedInterfaces;
		private final Set<String> knownInnerClasses = new HashSet<>();

		InjectingClassVisitor(int asmVersion, ClassWriter writer, List<InjectedInterface> injectedInterfaces) {
			super(asmVersion, writer);
			this.injectedInterfaces = injectedInterfaces;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			String[] baseInterfaces = interfaces.clone();
			Set<String> modifiedInterfaces = new LinkedHashSet<>(interfaces.length + injectedInterfaces.size());
			Collections.addAll(modifiedInterfaces, interfaces);

			for (InjectedInterface injectedInterface : injectedInterfaces) {
				modifiedInterfaces.add(injectedInterface.ifaceName());
			}

			// See JVMS: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-ClassSignature
			if (InjectedInterface.containsGenerics(injectedInterfaces) && signature == null) {
				// Classes that are not using generics don't need signatures, so their signatures are null
				// If the class is not using generics but that an injected interface targeting the class is using them, we are creating the class signature
				StringBuilder baseSignatureBuilder = new StringBuilder("L" + superName + ";");

				for (String baseInterface : baseInterfaces) {
					baseSignatureBuilder.append("L").append(baseInterface).append(";");
				}

				signature = baseSignatureBuilder.toString();
			}

			if (signature != null) {
				SignatureReader reader = new SignatureReader(signature);

				// Second Generics Check, if there are passed generics, are all of them present in the target class?
				GenericsChecker checker = new GenericsChecker(Constants.ASM_VERSION, injectedInterfaces);
				reader.accept(checker);

				var resultingSignature = new StringBuilder(signature);

				for (InjectedInterface injectedInterface : injectedInterfaces) {
					String superinterfaceSignature;

					if (injectedInterface.generics() != null) {
						superinterfaceSignature = "L" + injectedInterface.ifaceName() + injectedInterface.generics() + ";";
					} else {
						superinterfaceSignature = "L" + injectedInterface.ifaceName() + ";";
					}

					if (resultingSignature.indexOf(superinterfaceSignature) == -1) {
						resultingSignature.append(superinterfaceSignature);
					}
				}

				signature = resultingSignature.toString();
			}

			super.visit(version, access, name, signature, superName, modifiedInterfaces.toArray(new String[0]));
		}

		@Override
		public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
			this.knownInnerClasses.add(name);
			super.visitInnerClass(name, outerName, innerName, access);
		}

		@Override
		public void visitEnd() {
			// inject any necessary inner class entries
			// this may produce technically incorrect bytecode cuz we don't know the actual access flags for inner class entries
			// but it's hopefully enough to quiet some IDE errors
			for (final InjectedInterface itf : injectedInterfaces) {
				if (this.knownInnerClasses.contains(itf.ifaceName())) {
					continue;
				}

				int simpleNameIdx = itf.ifaceName().lastIndexOf('/');
				final String simpleName = simpleNameIdx == -1 ? itf.ifaceName() : itf.ifaceName().substring(simpleNameIdx + 1);
				int lastIdx = -1;
				int dollarIdx = -1;

				// Iterate through inner class entries starting from outermost to innermost
				while ((dollarIdx = simpleName.indexOf('$', dollarIdx + 1)) != -1) {
					if (dollarIdx - lastIdx == 1) {
						continue;
					}

					// Emit the inner class entry from this to the last one
					if (lastIdx != -1) {
						final String outerName = itf.ifaceName().substring(0, simpleNameIdx + 1 + lastIdx);
						final String innerName = simpleName.substring(lastIdx + 1, dollarIdx);
						super.visitInnerClass(outerName + '$' + innerName, outerName, innerName, INTERFACE_ACCESS);
					}

					lastIdx = dollarIdx;
				}

				// If we have a trailer to append
				if (lastIdx != -1 && lastIdx != simpleName.length()) {
					final String outerName = itf.ifaceName().substring(0, simpleNameIdx + 1 + lastIdx);
					final String innerName = simpleName.substring(lastIdx + 1);
					super.visitInnerClass(outerName + '$' + innerName, outerName, innerName, INTERFACE_ACCESS);
				}
			}

			super.visitEnd();
		}
	}

	private static class GenericsChecker extends SignatureVisitor {
		private final List<String> typeParameters;

		private final List<InjectedInterface> injectedInterfaces;

		GenericsChecker(int asmVersion, List<InjectedInterface> injectedInterfaces) {
			super(asmVersion);
			this.typeParameters = new ArrayList<>();
			this.injectedInterfaces = injectedInterfaces;
		}

		@Override
		public void visitFormalTypeParameter(String name) {
			this.typeParameters.add(name);
			super.visitFormalTypeParameter(name);
		}

		@Override
		public void visitEnd() {
			for (InjectedInterface injectedInterface : this.injectedInterfaces) {
				if (injectedInterface.generics() != null) {
					SignatureReader reader = new SignatureReader("Ljava/lang/Object" + injectedInterface.generics() + ";");
					GenericsConfirm confirm = new GenericsConfirm(
							Constants.ASM_VERSION,
							injectedInterface.className(),
							injectedInterface.ifaceName(),
							this.typeParameters
					);
					reader.accept(confirm);
				}
			}

			super.visitEnd();
		}

		public static class GenericsConfirm extends SignatureVisitor {
			private final String className;

			private final String interfaceName;

			private final List<String> acceptedTypeVariables;

			GenericsConfirm(int asmVersion, String className, String interfaceName, List<String> acceptedTypeVariables) {
				super(asmVersion);
				this.className = className;
				this.interfaceName = interfaceName;
				this.acceptedTypeVariables = acceptedTypeVariables;
			}

			@Override
			public void visitTypeVariable(String name) {
				if (!this.acceptedTypeVariables.contains(name)) {
					throw new IllegalStateException(
							"Interface "
							+ this.interfaceName
							+ " attempted to use a type variable named "
							+ name
							+ " which is not present in the "
							+ this.className
							+ " class"
					);
				}

				super.visitTypeVariable(name);
			}
		}
	}
}
