package net.fabricmc.example;

import com.google.gson.Gson;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;

public final class ShadowExample {
	public static String run() {
		return new Gson().toJson(StringUtils.capitalize(new Identifier("loom", "shadow").getPath()));
	}
}
