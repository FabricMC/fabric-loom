package net.fabricmc.loom.configuration.providers.forge;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import dev.architectury.mappingslayers.api.mutable.MappingsEntry;
import dev.architectury.mappingslayers.api.mutable.MutableClassDef;
import dev.architectury.mappingslayers.api.mutable.MutableFieldDef;
import dev.architectury.mappingslayers.api.mutable.MutableTinyTree;
import dev.architectury.mappingslayers.api.utils.MappingsUtils;
import dev.architectury.refmapremapper.utils.DescriptorRemapper;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ThreadingUtils;
import net.fabricmc.loom.util.srg.SrgMerger;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class FieldMigratedMappingsProvider extends MappingsProvider {
	private List<Map.Entry<FieldMember, String>> migratedFields = new ArrayList<>();
	public Path migratedFieldsCache;
	public Path rawTinyMappings;
	public Path rawTinyMappingsWithSrg;

	public FieldMigratedMappingsProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		LoomGradleExtension extension = getExtension();
		PatchProvider patchProvider = getExtension().getPatchProvider();
		migratedFieldsCache = patchProvider.getProjectCacheFolder().resolve("migrated-fields.json");
		migratedFields.clear();

		if (LoomGradlePlugin.refreshDeps) {
			Files.deleteIfExists(migratedFieldsCache);
		} else if (Files.exists(migratedFieldsCache)) {
			try (BufferedReader reader = Files.newBufferedReader(migratedFieldsCache)) {
				Map<String, String> map = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {
				}.getType());
				migratedFields = new ArrayList<>();
				map.forEach((key, newDescriptor) -> {
					String[] split = key.split("#");
					migratedFields.add(new AbstractMap.SimpleEntry<>(new FieldMember(split[0], split[1]), newDescriptor));
				});
			}
		}

		super.provide(dependency, postPopulationScheduler);
	}

	@Override
	public void manipulateMappings(Path mappingsJar) throws IOException {
		LoomGradleExtension extension = getExtension();
		Path mappingsFolder = mappingsDir.resolve(extension.getMinecraftProvider().getMinecraftVersion() + "/forge-" + extension.getPatchProvider().forgeVersion);
		this.rawTinyMappings = tinyMappings.toPath();
		this.rawTinyMappingsWithSrg = tinyMappingsWithSrg;
		String mappingsJarName = mappingsJar.getFileName().toString();

		if (getExtension().shouldGenerateSrgTiny()) {
			if (Files.notExists(rawTinyMappingsWithSrg) || isRefreshDeps()) {
				// Merge tiny mappings with srg
				SrgMerger.mergeSrg(getExtension().getSrgProvider().getSrg().toPath(), rawTinyMappings, rawTinyMappingsWithSrg, true);
			}
		}

		try {
			Files.createDirectories(mappingsFolder);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		tinyMappings = mappingsFolder.resolve("mappings.tiny").toFile();
		tinyMappingsJar = mappingsFolder.resolve("mappings.jar").toFile();
		tinyMappingsWithSrg = mappingsFolder.resolve("mappings-srg.tiny");
		mixinTinyMappingsWithSrg = mappingsFolder.resolve("mixin-srg.tiny").toFile();
		srgToNamedSrg = mappingsFolder.resolve("srg-to-named.srg").toFile();

		try {
			updateFieldMigration();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void updateFieldMigration() throws IOException {
		if (!Files.exists(migratedFieldsCache)) {
			generateNewFieldMigration();
			Map<String, String> map = new HashMap<>();
			migratedFields.forEach(entry -> {
				map.put(entry.getKey().owner + "#" + entry.getKey().field, entry.getValue());
			});
			Files.write(migratedFieldsCache, new Gson().toJson(map).getBytes(StandardCharsets.UTF_8));
			Files.deleteIfExists(tinyMappings.toPath());
		}

		if (!Files.exists(tinyMappings.toPath())) {
			Table<String, String, String> fieldDescriptorMap = HashBasedTable.create();

			for (Map.Entry<FieldMember, String> entry : migratedFields) {
				fieldDescriptorMap.put(entry.getKey().owner, entry.getKey().field, entry.getValue());
			}

			MutableTinyTree mappings;

			try (BufferedReader reader = Files.newBufferedReader(rawTinyMappings)) {
				mappings = MappingsUtils.copyAsMutable(TinyMappingFactory.loadWithDetection(reader));

				for (MutableClassDef classDef : mappings.getClassesMutable()) {
					Map<String, String> row = fieldDescriptorMap.row(classDef.getIntermediary());

					if (!row.isEmpty()) {
						for (MutableFieldDef fieldDef : classDef.getFieldsMutable()) {
							String newDescriptor = row.get(fieldDef.getIntermediary());

							if (newDescriptor != null) {
								fieldDef.setDescriptor(MappingsEntry.NS_INTERMEDIARY, newDescriptor);
							}
						}
					}
				}
			}

			Files.write(tinyMappings.toPath(), MappingsUtils.serializeToString(mappings).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
		}
	}

	private void generateNewFieldMigration() throws IOException {
		Map<FieldMember, String> fieldDescriptorMap = new ConcurrentHashMap<>();
		LoomGradleExtension extension = getExtension();
		ThreadingUtils.TaskCompleter completer = ThreadingUtils.taskCompleter();

		class Visitor extends ClassVisitor {
			private final ThreadLocal<String> lastClass = new ThreadLocal<>();

			Visitor(int api) {
				super(api);
			}

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				lastClass.set(name);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				fieldDescriptorMap.put(new FieldMember(lastClass.get(), name), descriptor);
				return super.visitField(access, name, descriptor, signature, value);
			}
		}

		Visitor visitor = new Visitor(Opcodes.ASM9);

		for (MinecraftPatchedProvider.Environment environment : MinecraftPatchedProvider.Environment.values()) {
			File patchedSrgJar = environment.patchedSrgJar.apply(extension.getMappingsProvider().patchedProvider);
			FileSystemUtil.FileSystemDelegate system = FileSystemUtil.getJarFileSystem(patchedSrgJar, false);
			completer.onComplete(value -> system.close());

			for (Path fsPath : (Iterable<? extends Path>) Files.walk(system.get().getPath("/"))::iterator) {
				if (Files.isRegularFile(fsPath) && fsPath.toString().endsWith(".class")) {
					completer.add(() -> {
						byte[] bytes = Files.readAllBytes(fsPath);
						new ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					});
				}
			}
		}

		completer.complete();
		Map<FieldMember, String> migratedFields = new HashMap<>();

		try (BufferedReader reader = Files.newBufferedReader(rawTinyMappingsWithSrg)) {
			TinyTree mappings = TinyMappingFactory.loadWithDetection(reader);
			Map<String, String> srgToIntermediary = new HashMap<>();

			for (ClassDef aClass : mappings.getClasses()) {
				srgToIntermediary.put(aClass.getName("srg"), aClass.getName("intermediary"));
			}

			for (ClassDef classDef : mappings.getClasses()) {
				String ownerSrg = classDef.getName("srg");
				String ownerIntermediary = classDef.getName("intermediary");

				for (FieldDef fieldDef : classDef.getFields()) {
					String fieldSrg = fieldDef.getName("srg");
					String descriptorSrg = fieldDef.getDescriptor("srg");

					FieldMember member = new FieldMember(ownerSrg, fieldSrg);
					String newDescriptor = fieldDescriptorMap.get(member);

					if (newDescriptor != null && !newDescriptor.equals(descriptorSrg)) {
						String fieldIntermediary = fieldDef.getName("intermediary");
						String descriptorIntermediary = fieldDef.getDescriptor("intermediary");
						String newDescriptorRemapped = DescriptorRemapper.remapDescriptor(newDescriptor,
								clazz -> srgToIntermediary.getOrDefault(clazz, clazz));
						migratedFields.put(new FieldMember(ownerIntermediary, fieldIntermediary), newDescriptorRemapped);
						getProject().getLogger().info(ownerIntermediary + "#" + fieldIntermediary + ": " + descriptorIntermediary + " -> " + newDescriptorRemapped);
					}
				}
			}
		}

		this.migratedFields.clear();
		this.migratedFields.addAll(migratedFields.entrySet());
	}

	public static class FieldMember {
		public String owner;
		public String field;

		public FieldMember(String owner, String field) {
			this.owner = owner;
			this.field = field;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			FieldMember that = (FieldMember) o;
			return Objects.equals(owner, that.owner) && Objects.equals(field, that.field);
		}

		@Override
		public int hashCode() {
			return Objects.hash(owner, field);
		}
	}
}
