package net.fabricmc.loom.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loom.ModExtension;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;

public class ModJsonUpdater {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void updateModJson(ModExtension modExtension, File jarFile) {
		Set<String> modJsonFileNames = new HashSet<String>();
		modJsonFileNames.add("mod.json");
		ZipUtil.transformEntries(
				jarFile,
				modJsonFileNames.stream()
						.map((f) -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
							@Override
							protected String transform(ZipEntry zipEntry, String input) throws IOException {
								JsonObject json = GSON.fromJson(input, JsonObject.class);
								if(modExtension.getId() != null) {
									json.add("id", new JsonPrimitive(modExtension.getId()));
								}
								if(modExtension.getVersion() != null) {
									json.add("version", new JsonPrimitive(modExtension.getVersion()));
								}
								return GSON.toJson(json);
							}
						})).toArray(ZipEntryTransformerEntry[]::new)
		);
	}
}
