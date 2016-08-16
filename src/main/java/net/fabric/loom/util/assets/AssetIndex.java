package net.fabric.loom.util.assets;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class AssetIndex {
    private final Map<String, AssetObject> objects;
    private boolean virtual;

    public AssetIndex() {
        this.objects = new LinkedHashMap<>();
    }

    public Map<String, AssetObject> getFileMap() {
        return this.objects;
    }

    public Set<AssetObject> getUniqueObjects() {
        return new HashSet<>(this.objects.values());
    }

    public boolean isVirtual() {
        return this.virtual;
    }
}
