package net.fabricmc.loom.configuration.providers.mappings.tiny;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public class TinyMappingsSpecBuilder {
	private static final String DEFAULT_MAPPING_PATH = "mappings/mappings.tiny";

	private final String dependencyNotation;
	private @Nullable String mappingPath = DEFAULT_MAPPING_PATH;

	private TinyMappingsSpecBuilder(String dependencyNotation) {
		this.dependencyNotation = Objects.requireNonNull(dependencyNotation, "dependency notation cannot be null");
	}

	public static TinyMappingsSpecBuilder builder(String dependencyNotation) {
		return new TinyMappingsSpecBuilder(dependencyNotation);
	}

	/**
	 * Makes this mappings spec read bare files instead of zips or jars.
	 *
	 * @return this builder
	 */
	public TinyMappingsSpecBuilder bareFile() {
		this.mappingPath = null;
		return this;
	}

	/**
	 * Sets the mapping path inside a zip or jar.
	 * If the specified path is null, behaves like {@link #bareFile()}.
	 *
	 * @param mappingPath the mapping path, or null if a bare file
	 * @return this builder
	 */
	public TinyMappingsSpecBuilder mappingPath(@Nullable String mappingPath) {
		this.mappingPath = mappingPath;
		return this;
	}

	public TinyMappingsSpec build() {
		return new TinyMappingsSpec(dependencyNotation, mappingPath);
	}
}
