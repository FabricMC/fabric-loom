package net.fabricmc.loom.util.srg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class InnerClassRemapper {
	public static IMappingProvider of(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		return sink -> {
			remapInnerClass(fromJar, mappingsWithSrg, from, to, sink::acceptClass);
		};
	}

	private static void remapInnerClass(Path fromJar, TinyTree mappingsWithSrg, String from, String to, BiConsumer<String, String> action) {
		try (InputStream inputStream = Files.newInputStream(fromJar)) {
			Map<String, String> availableClasses = mappingsWithSrg.getClasses().stream()
					.collect(Collectors.groupingBy(classDef -> classDef.getName(from),
							Collectors.<ClassDef, String>reducing(
									null,
									classDef -> classDef.getName(to),
									(first, last) -> last
							))
					);
			ZipUtil.iterate(inputStream, (in, zipEntry) -> {
				if (!zipEntry.isDirectory() && zipEntry.getName().contains("$") && zipEntry.getName().endsWith(".class")) {
					String className = zipEntry.getName().substring(0, zipEntry.getName().length() - 6);

					if (!availableClasses.containsKey(className)) {
						String parentName = className.substring(0, className.indexOf('$'));
						String childName = className.substring(className.indexOf('$') + 1);
						String remappedParentName = availableClasses.getOrDefault(parentName, parentName);
						String remappedName = remappedParentName + "$" + childName;

						if (!className.equals(remappedName)) {
							action.accept(className, remappedName);
						}
					}
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
