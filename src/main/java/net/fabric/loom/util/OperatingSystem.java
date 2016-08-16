package net.fabric.loom.util;

public class OperatingSystem {
    public static String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac")) {
            return "osx";
        } else {
            return "linux";
        }
    }

    public static String getArch() {
        if (is64Bit()) {
            return "64";
        } else {
            return "32";
        }
    }

    public static boolean is64Bit() {
        return System.getProperty("sun.arch.data.model").contains("64");
    }
}
