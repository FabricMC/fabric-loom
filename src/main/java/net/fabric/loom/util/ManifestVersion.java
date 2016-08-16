package net.fabric.loom.util;

import java.util.ArrayList;
import java.util.List;

public class ManifestVersion {
    public List<Versions> versions = new ArrayList<>();

    public static class Versions {
        public String id, url;
    }
}
