package chorusmc.gradle.util;

import chorusmc.gradle.util.delayed.IDelayed;
import chorusmc.gradle.util.delayed.DelayedFile;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class Constants {
    public static final File WORKING_DIRECTORY = new File(".");
    public static final File CACHE_FILES = new File(WORKING_DIRECTORY, ".gradle/minecraft");

    public static final IDelayed<File> MINECRAFT_CLIENT_JAR = new DelayedFile(extension -> new File(CACHE_FILES, extension.version + "-client.jar"));
    public static final IDelayed<File> MINECRAFT_CLIENT_MAPPED_JAR = new DelayedFile(extension -> new File(CACHE_FILES, extension.version + "-client-mapped.jar"));

    public static final File MAPPINGS_ZIP = new File(CACHE_FILES, "mappings.zip");
    public static final File MAPPINGS_DIR = new File(CACHE_FILES, "mappings");

    public static final File MINECRAFT_LIBS = new File(CACHE_FILES, "libs");
    public static final File MINECRAFT_NATIVES = new File(CACHE_FILES, "natives");
    public static final IDelayed<File> MINECRAFT_JSON = new DelayedFile(extension -> new File(CACHE_FILES, extension.version + "-info.json"));

    public static final File MINECRAFT_ROOT = new File(WORKING_DIRECTORY, "minecraft");
    public static final IDelayed<File> MAPPING_SRG = new DelayedFile(extension -> new File(WORKING_DIRECTORY, "mappings.srg"));

    public static final File VERSION_MANIFEST = new File(CACHE_FILES, "version_manifest.json");

    public static final String LIBRARIES_BASE = "https://libraries.minecraft.net/";
    public static final String RESOURCES_BASE = "http://resources.download.minecraft.net/";

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";

    public static final String CONFIG_NATIVES = "MC_NATIVES";
    public static final String CONFIG_MC_DEPENDENCIES = "MC_DEPENDENCIES";
    public static final String CONFIG_MC_DEPENDENCIES_CLIENT = "MC_DEPENDENCIES_CLIENT";
    public static final String SYSTEM_ARCH = System.getProperty("os.arch").equals("64") ? "64" : "32";

    public static List<String> getClassPath() {
        URL[] urls = ((URLClassLoader) Constants.class.getClassLoader()).getURLs();
        ArrayList<String> list = new ArrayList<>();
        for (URL url : urls) {
            list.add(url.getPath());
        }
        return list;
    }
}
