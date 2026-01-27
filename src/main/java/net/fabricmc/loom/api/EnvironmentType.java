package net.fabricmc.loom.api;

public enum EnvironmentType {
	MERGED,
	SPLIT,
	LEGACY_SPLIT;

	public boolean isSplit() {
		return this == SPLIT || this == LEGACY_SPLIT;
	}
}
