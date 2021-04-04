package net.fabricmc.loom.configuration.accesstransformer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.shedaniel.architectury.refmapremapper.remapper.SimpleReferenceRemapper;
import net.minecraftforge.accesstransformer.AccessTransformerEngine;
import net.minecraftforge.accesstransformer.TransformerProcessor;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.util.Strings;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.JarUtil;
import net.fabricmc.loom.util.srg.AtRemapper;
import net.fabricmc.mapping.tree.TinyTree;

public class AccessTransformerJarProcessor implements JarProcessor {
	private final Path projectAt;
	private final Project project;
	private final Set<String> affectedClasses = new HashSet<>();
	private Path remappedProjectAt;

	public AccessTransformerJarProcessor(Path projectAt, Project project) {
		this.projectAt = projectAt;
		this.project = project;
	}

	public static void addTo(Project project, LoomGradleExtension extension) {
		SourceSet main = project.getConvention().findPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");

		for (File srcDir : main.getResources().getSrcDirs()) {
			File projectAt = new File(srcDir, "META-INF/accesstransformer.cfg");

			if (projectAt.exists()) {
				extension.addJarProcessor(new AccessTransformerJarProcessor(projectAt.toPath(), project));
				return;
			}
		}
	}

	@Override
	public void setup() {
		affectedClasses.clear();
		remappedProjectAt = null;
		getRemappedAt();
	}

	@Override
	public void process(File file) {
		try {
			accessTransformForge(file, file, false, getRemappedAt());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, "loom-at.sha256");

		if (hash == null) {
			return true;
		}

		return !Arrays.equals(getInputHash(false, getRemappedAt()), hash);
	}

	@Override
	public boolean doesProcessClass(String classInternalName) {
		getRemappedAt();
		return affectedClasses.contains(classInternalName);
	}

	private File getRemappedAt() {
		if (remappedProjectAt == null) {
			try {
				LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
				MappingsProvider mappingsProvider = extension.getMappingsProvider();
				TinyTree mappings = extension.isForge() ? mappingsProvider.getMappingsWithSrg() : mappingsProvider.getMappings();

				File file = File.createTempFile("at-remapped-", ".at");
				file.delete();
				String sourceAt = FileUtils.readFileToString(projectAt.toFile(), StandardCharsets.UTF_8);
				String remappedAt = AtRemapper.remapAt(project.getLogger(), sourceAt, new SimpleReferenceRemapper.Remapper() {
					@Override
					@Nullable
					public String mapClass(String value) {
						return mappings.getClasses().stream()
								.filter(classDef -> Objects.equals(classDef.getName("srg"), value))
								.findFirst()
								.map(classDef -> classDef.getName("named"))
								.orElse(null);
					}

					@Override
					@Nullable
					public String mapMethod(@Nullable String className, String methodName, String methodDescriptor) {
						return mappings.getClasses().stream()
								.flatMap(classDef -> classDef.getMethods().stream())
								.filter(methodDef -> Objects.equals(methodDef.getName("srg"), methodName))
								.filter(methodDef -> Objects.equals(methodDef.getDescriptor("srg"), methodDescriptor))
								.findFirst()
								.map(classDef -> classDef.getName("named"))
								.orElse(null);
					}

					@Override
					@Nullable
					public String mapField(@Nullable String className, String fieldName, String fieldDescriptor) {
						return mappings.getClasses().stream()
								.flatMap(classDef -> classDef.getFields().stream())
								.filter(methodDef -> Objects.equals(methodDef.getName("srg"), fieldName))
								.findFirst()
								.map(classDef -> classDef.getName("named"))
								.orElse(null);
					}
				});

				for (String line : remappedAt.split("\n")) {
					{
						int indexOf = line.indexOf('#');

						if (indexOf != -1) {
							line = line.substring(0, indexOf);
						}

						line = line.trim();
					}

					if (!Strings.isBlank(line)) {
						affectedClasses.add(line.split("\\s+")[1].replace('.', '/'));
					}
				}

				FileUtils.write(file, remappedAt, StandardCharsets.UTF_8);
				file.deleteOnExit();
				return (remappedProjectAt = file.toPath()).toFile();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		return remappedProjectAt.toFile();
	}

	public static byte[] getInputHash(boolean addEmbeddedAt, File... extraAts) {
		byte[] inputHash = new byte[] {(byte) (addEmbeddedAt ? 1 : 0)};

		for (File extraAt : extraAts) {
			inputHash = concat(inputHash, Checksum.sha256(extraAt));
		}

		return inputHash;
	}

	public static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	public static void accessTransformForge(File input, File target, boolean addEmbeddedAt, File... extraAts) throws Exception {
		byte[] inputHash = getInputHash(addEmbeddedAt, extraAts);
		File inputCopied = File.createTempFile("at-input-", ".jar");
		File at = File.createTempFile("at-", ".cfg");
		FileUtils.copyFile(input, inputCopied);

		if (target.exists()) {
			byte[] hash = ZipUtil.unpackEntry(target, "loom-at.sha256");

			if (hash != null && Arrays.equals(inputHash, hash)) {
				return;
			}
		}

		target.delete();
		String[] args = new String[] {
				"--inJar", inputCopied.getAbsolutePath(),
				"--outJar", target.getAbsolutePath()
		};

		if (addEmbeddedAt) {
			JarUtil.extractFile(inputCopied, "META-INF/accesstransformer.cfg", at);
			args = Arrays.copyOf(args, args.length + 2);
			args[args.length - 2] = "--atFile";
			args[args.length - 1] = at.getAbsolutePath();
		}

		for (File extraAt : extraAts) {
			args = Arrays.copyOf(args, args.length + 2);
			args[args.length - 2] = "--atFile";
			args[args.length - 1] = extraAt.getAbsolutePath();
		}

		resetAccessTransformerEngine();
		TransformerProcessor.main(args);
		inputCopied.delete();
		at.delete();
		resetAccessTransformerEngine();

		ZipUtil.addEntry(target, "loom-at.sha256", inputHash);
	}

	private static void resetAccessTransformerEngine() throws Exception {
		// Thank you Forge, I love you
		Field field = AccessTransformerEngine.class.getDeclaredField("masterList");
		field.setAccessible(true);
		AccessTransformerList list = (AccessTransformerList) field.get(AccessTransformerEngine.INSTANCE);
		field = AccessTransformerList.class.getDeclaredField("accessTransformers");
		field.setAccessible(true);
		((Map<?, ?>) field.get(list)).clear();
	}
}
