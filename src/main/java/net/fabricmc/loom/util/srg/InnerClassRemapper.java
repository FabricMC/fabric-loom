package net.fabricmc.loom.util.srg;

import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import org.zeroturnaround.zip.ZipUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InnerClassRemapper {
	public static IMappingProvider of(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		Map<String, String> map = buildInnerClassRemap(fromJar, mappingsWithSrg, from, to);
		return sink -> {
			for (Map.Entry<String, String> entry : map.entrySet()) {
				sink.acceptClass(entry.getKey(), entry.getValue());
			}
		};
	}

	private static Map<String, String> buildInnerClassRemap(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		Map<String, String> remapInnerClasses = new HashMap<>();

		try (InputStream inputStream = Files.newInputStream(fromJar)) {
			Set<String> availableClasses = mappingsWithSrg.getClasses().stream()
					.map(classDef -> classDef.getName(from))
					.collect(Collectors.toSet());
			ZipUtil.iterate(inputStream, (in, zipEntry) -> {
				if (!zipEntry.isDirectory() && zipEntry.getName().contains("$") && zipEntry.getName().endsWith(".class")) {
					String className = zipEntry.getName().substring(0, zipEntry.getName().length() - 6);
					if (!availableClasses.contains(className)) {
						String parentName = className.substring(0, className.indexOf('$'));
						String childName = className.substring(className.indexOf('$') + 1);
						String remappedParentName = mappingsWithSrg.getClasses().stream()
								.filter(classDef -> Objects.equals(classDef.getName(from), parentName))
								.findFirst()
								.map(classDef -> classDef.getName(to))
								.orElse(parentName);
						String remappedName = remappedParentName + "$" + childName;
						if (!className.equals(remappedName)) {
							remapInnerClasses.put(className, remappedName);
						}
					}
				}
			});
		}

		return remapInnerClasses;
	}
}
