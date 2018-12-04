package net.fabricmc.loom.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.ModExtension;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

public class ModJsonUpdater {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void updateModJson(ModExtension modExtension, File jarFile) {
		Set<String> modJsonFileNames = new HashSet<>();
		modJsonFileNames.add("mod.json");
		ZipUtil.transformEntries(
				jarFile,
				modJsonFileNames.stream()
						.map((f) -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
							@Override
							protected String transform(ZipEntry zipEntry, String json) {
								for (Map.Entry<String, String> entry : modExtension.getReplacements().entrySet()) {
									json = json.replace(entry.getKey(), entry.getValue());
								}
								return json;
							}
						})).toArray(ZipEntryTransformerEntry[]::new)
		);
	}
}
