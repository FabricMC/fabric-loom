package net.fabric.loom.util.assets;

public class AssetObject {
    private String hash;
    private long size;

    public String getHash() {
        return this.hash;
    }

    public long getSize() {
        return this.size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if ((o == null) || (getClass() != o.getClass())) {
            return false;
        } else {
            AssetObject that = (AssetObject) o;
            return this.size == that.size && this.hash.equals(that.hash);
        }
    }

    @Override
    public int hashCode() {
        int result = this.hash.hashCode();
        result = 31 * result + (int) (this.size ^ this.size >>> 32);
        return result;
    }
}