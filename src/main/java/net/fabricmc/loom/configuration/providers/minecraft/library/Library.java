package net.fabricmc.loom.configuration.providers.minecraft.library;

import org.jetbrains.annotations.Nullable;

public record Library(String group, String name, String version, @Nullable String classifier, Target target) {
	public enum Target {
		/**
		 * A runtime only library
		 */
		RUNTIME,
		/**
		 * A runtime and compile library
		 */
		COMPILE,
		/**
		 * Natives
		 */
		NATIVES,
		/**
		 * A mod library that needs remapping
		 */
		LOCAL_MOD
	}

	public static Library fromMaven(String name, Target target) {
		String[] split = name.split(":");
		assert split.length == 3 || split.length == 4;

		return new Library(split[0], split[1], split[2], split.length == 4 ? split[3] : null, target);
	}

	/**
	 * Returns true when the group or the group and name match.
	 *
	 * @param str Takes a string containing the maven group, or a group and name split by :
	 * @return true when the group or the group and name match.
	 */
	public boolean is(String str) {
		if (str.contains(":")) {
			final String[] split = str.split(":");
			assert split.length == 2;
			return this.group.equals(split[0]) && this.name.equals(split[1]);
		}

		return this.group.equals(str);
	}

	public Library withVersion(String version) {
		return new Library(this.group, this.name, version, this.classifier, this.target);
	}

	public Library withClassifier(@Nullable String classifier) {
		return new Library(this.group, this.name, this.version, classifier, this.target);
	}

	public Library withTarget(Target target) {
		return new Library(this.group, this.name, this.version, this.classifier, target);
	}
}
