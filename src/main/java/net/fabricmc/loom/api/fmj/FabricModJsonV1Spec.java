/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.api.fmj;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the Fabric mod JSON v1 specification.
 *
 * <p>This class defines properties of a Fabric mod JSON file via a type-safe DSL.
 */
public abstract class FabricModJsonV1Spec {
	/**
	 * The ID of the mod.
	 * @return A {@link Property} containing a {@link String}  representing the mod ID
	 */
	@Input
	public abstract Property<String> getModId();

	/**
	 * The version of the mod.
	 * @return A {@link Property} containing a {@link String} representing the mod version
	 */
	@Input
	public abstract Property<String> getVersion();

	/**
	 * The display name of the mod.
	 * @return A {@link Property} containing a {@link String} representing the mod name
	 */
	@Input
	@Optional
	public abstract Property<String> getName();

	/**
	 * The description of the mod.
	 * @return A {@link Property} containing a {@link String} representing the mod description
	 */
	@Input
	@Optional
	public abstract Property<String> getDescription();

	/**
	 * A list of other mod IDs that this mod uses as aliases.
	 * @return A {@link ListProperty} containing a list of {@link String} representing the mod aliases
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getProvides();

	/**
	 * The environment the mod runs in.
	 *
	 * <p>One of `client`, `server`, or `*`.
	 *
	 * @return A {@link Property} containing a {@link String} representing the mod environment
	 */
	@Input
	@Optional
	public abstract Property<String> getEnvironment();

	/**
	 * Sets the environment to 'client', indicating the mod only runs on the client side.
	 */
	public void client() {
		getEnvironment().set("client");
	}

	/**
	 * Sets the environment to 'server', indicating the mod only runs on the server side.
	 */
	public void server() {
		getEnvironment().set("server");
	}

	/**
	 * A list of entrypoints for the mod.
	 * @return A {@link ListProperty} containing a list of {@link Entrypoint} representing the mod entrypoints
	 */
	@Input
	@Optional
	public abstract ListProperty<Entrypoint> getEntrypoints();

	/**
	 * Add a new entrypoint with the given name and value.
	 *
	 * @param entrypoint The name of the entrypoint, such as "main" or "client"
	 * @param value The value of the entrypoint, typically a fully qualified class name
	 */
	public void entrypoint(String entrypoint, String value) {
		entrypoint(entrypoint, value, metadata -> { });
	}

	/**
	 * Add a new entrypoint with the given name, value, and language adapter.
	 *
	 * @param entrypoint The name of the entrypoint, such as "main" or "client"
	 * @param value The value of the entrypoint, typically a fully qualified class name
	 * @param adapter The language adapter of the entrypoint, such as "kotlin" or "scala"
	 */
	public void entrypoint(String entrypoint, String value, String adapter) {
		entrypoint(entrypoint, value, metadata -> {
			metadata.getAdapter().set(adapter);
		});
	}

	/**
	 * Add a new entrypoint with the given name and value, and configure it with the given action.
	 *
	 * @param entrypoint The name of the entrypoint, such as "main" or "client"
	 * @param value The value of the entrypoint, typically a fully qualified class name
	 * @param action An action to configure the entrypoint further
	 */
	public void entrypoint(String entrypoint, String value, Action<Entrypoint> action) {
		entrypoint(entrypoint, metadata -> {
			metadata.getValue().set(value);
			action.execute(metadata);
		});
	}

	/**
	 * Add a new entrypoint with the given name, and configure it with the given action.
	 *
	 * @param entrypoint The name of the entrypoint, such as "main" or "client"
	 * @param action An action to configure the entrypoint
	 */
	public void entrypoint(String entrypoint, Action<Entrypoint> action) {
		create(Entrypoint.class, getEntrypoints(), e -> {
			e.getEntrypoint().set(entrypoint);
			action.execute(e);
		});
	}

	/**
	 * A list of additional JARs to load with the mod.
	 *
	 * <p>A path relative to the root of the mod JAR.
	 *
	 * @return A {@link ListProperty} containing a list of {@link String} representing the additional JARs
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getJars();

	/**
	 * A list of Mixin configurations for the mod.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Mixin} representing the mod Mixins
	 */
	@Input
	@Optional
	public abstract ListProperty<Mixin> getMixins();

	/**
	 * Add a new Mixin configuration with the given value.
	 *
	 * @param value The value of the Mixin configuration, typically a path to a JSON file
	 */
	public void mixin(String value) {
		mixin(value, mixin -> { });
	}

	/**
	 * Add a new Mixin configuration with the given value, and configure it with the given action.
	 *
	 * @param value The value of the Mixin configuration, typically a path to a JSON file
	 * @param action An action to configure the Mixin further
	 */
	public void mixin(String value, Action<Mixin> action) {
		mixin(mixin -> {
			mixin.getValue().set(value);
			action.execute(mixin);
		});
	}

	/**
	 * Add a new Mixin configuration, and configure it with the given action.
	 *
	 * @param action An action to configure the Mixin
	 */
	public void mixin(Action<Mixin> action) {
		create(Mixin.class, getMixins(), action);
	}

	/**
	 * The path to the access widener file for the mod.
	 *
	 * <p>A path relative to the root of the mod JAR.
	 *
	 * @return A {@link Property} containing a {@link String} representing the access widener path
	 */
	@Input
	@Optional
	public abstract Property<String> getAccessWidener();

	/**
	 * A list of depedencies that this mod depends on (required).
	 * @return A {@link ListProperty} containing a list of {@link Dependency} representing the mod dependencies
	 */
	@Input
	@Optional
	public abstract ListProperty<Dependency> getDepends();

	/**
	 * Add a required dependency on another mod with the given mod ID and version requirements.
	 *
	 * @param modId The mod ID of the dependency
	 * @param versionRequirements A collection of version requirement strings
	 */
	public void depends(String modId, Iterable<String> versionRequirements) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	/**
	 * Add a required dependency on another mod with the given mod ID and a single version requirement.
	 *
	 * @param modId The mod ID of the dependency
	 * @param versionRequirement A version requirement string
	 */
	public void depends(String modId, String versionRequirement) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	/**
	 * Add a required dependency on another mod with the given mod ID, and configure it with the given action.
	 *
	 * @param modId The mod ID of the dependency
	 * @param action An action to configure the dependency further
	 */
	public void depends(String modId, Action<Dependency> action) {
		depends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	/**
	 * Add a required dependency, and configure it with the given action.
	 *
	 * @param action An action to configure the dependency
	 */
	public void depends(Action<Dependency> action) {
		create(Dependency.class, getDepends(), action);
	}

	/**
	 * A list of recommended dependencies.
	 * @return A {@link ListProperty} containing a list of {@link Dependency} representing the mod recommended dependencies
	 */
	@Input
	@Optional
	public abstract ListProperty<Dependency> getRecommends();

	/**
	 * Add a recommended dependency on another mod with the given mod ID and version requirements.
	 *
	 * @param modId The mod ID of the recommended dependency
	 * @param versionRequirements A collection of version requirement strings
	 */
	public void recommends(String modId, Iterable<String> versionRequirements) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	/**
	 * Add a recommended dependency on another mod with the given mod ID and a single version requirement.
	 *
	 * @param modId The mod ID of the recommended dependency
	 * @param versionRequirement A version requirement string
	 */
	public void recommends(String modId, String versionRequirement) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	/**
	 * Add a recommended dependency on another mod with the given mod ID, and configure it with the given action.
	 *
	 * @param modId The mod ID of the recommended dependency
	 * @param action An action to configure the recommended dependency further
	 */
	public void recommends(String modId, Action<Dependency> action) {
		recommends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	/**
	 * Add a recommended dependency, and configure it with the given action.
	 *
	 * @param action An action to configure the recommended dependency
	 */
	public void recommends(Action<Dependency> action) {
		create(Dependency.class, getRecommends(), action);
	}

	/**
	 * A list of suggested dependencies.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Dependency} representing the mod suggested dependencies
	 */
	@Input
	@Optional
	public abstract ListProperty<Dependency> getSuggests();

	/**
	 * Add a suggested dependency on another mod with the given mod ID and version requirements.
	 *
	 * @param modId The mod ID of the suggested dependency
	 * @param versionRequirements A collection of version requirement strings
	 */
	public void suggests(String modId, Iterable<String> versionRequirements) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	/**
	 * Add a suggested dependency on another mod with the given mod ID and a single version requirement.
	 *
	 * @param modId The mod ID of the suggested dependency
	 * @param versionRequirement A version requirement string
	 */
	public void suggests(String modId, String versionRequirement) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	/**
	 * Add a suggested dependency on another mod with the given mod ID, and configure it with the given action.
	 *
	 * @param modId The mod ID of the suggested dependency
	 * @param action An action to configure the suggested dependency further
	 */
	public void suggests(String modId, Action<Dependency> action) {
		suggests(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	/**
	 * Add a suggested dependency, and configure it with the given action.
	 *
	 * @param action An action to configure the suggested dependency
	 */
	public void suggests(Action<Dependency> action) {
		create(Dependency.class, getSuggests(), action);
	}

	/**
	 * A list of conflicting dependencies.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Dependency} representing the mod conflicting dependencies
	 */
	@Input
	@Optional
	public abstract ListProperty<Dependency> getConflicts();

	/**
	 * Add a conflicting dependency on another mod with the given mod ID and version requirements.
	 *
	 * @param modId The mod ID of the conflicting dependency
	 * @param versionRequirements A collection of version requirement strings
	 */
	public void conflicts(String modId, Iterable<String> versionRequirements) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	/**
	 * Add a conflicting dependency on another mod with the given mod ID and a single version requirement.
	 *
	 * @param modId The mod ID of the conflicting dependency
	 * @param versionRequirement A version requirement string
	 */
	public void conflicts(String modId, String versionRequirement) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	/**
	 * Add a conflicting dependency on another mod with the given mod ID, and configure it with the given action.
	 *
	 * @param modId The mod ID of the conflicting dependency
	 * @param action An action to configure the conflicting dependency further
	 */
	public void conflicts(String modId, Action<Dependency> action) {
		conflicts(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	/**
	 * Add a conflicting dependency, and configure it with the given action.
	 *
	 * @param action An action to configure the conflicting dependency
	 */
	public void conflicts(Action<Dependency> action) {
		create(Dependency.class, getConflicts(), action);
	}

	/**
	 * A list of dependencies that this mod breaks.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Dependency} representing the mod broken dependencies
	 */
	@Input
	@Optional
	public abstract ListProperty<Dependency> getBreaks();

	/**
	 * Add a broken dependency on another mod with the given mod ID and version requirements.
	 *
	 * @param modId The mod ID of the broken dependency
	 * @param versionRequirements A collection of version requirement strings
	 */
	public void breaks(String modId, Iterable<String> versionRequirements) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	/**
	 * Add a broken dependency on another mod with the given mod ID and a single version requirement.
	 *
	 * @param modId The mod ID of the broken dependency
	 * @param versionRequirement A version requirement string
	 */
	public void breaks(String modId, String versionRequirement) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	/**
	 * Add a broken dependency on another mod with the given mod ID, and configure it with the given action.
	 *
	 * @param modId The mod ID of the broken dependency
	 * @param action An action to configure the broken dependency further
	 */
	public void breaks(String modId, Action<Dependency> action) {
		breaks(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	/**
	 * Add a broken dependency, and configure it with the given action.
	 *
	 * @param action An action to configure the broken dependency
	 */
	public void breaks(Action<Dependency> action) {
		create(Dependency.class, getBreaks(), action);
	}

	/**
	 * A list of licenses for the mod.
	 *
	 * @return A {@link ListProperty} containing a list of {@link String} representing the mod licenses
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getLicenses();

	/**
	 * A list of authors of the mod.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Person} representing the mod authors
	 */
	@Input
	@Optional
	public abstract ListProperty<Person> getAuthors();

	/**
	 * Add a new author with the given name.
	 *
	 * @param name The name of the author
	 */
	public void author(String name) {
		author(name, person -> { });
	}

	/**
	 * Add a new author with the given name, and configure it with the given action.
	 *
	 * @param name The name of the author
	 * @param action An action to configure the author further
	 */
	public void author(String name, Action<Person> action) {
		author(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	/**
	 * Add a new author, and configure it with the given action.
	 *
	 * @param action An action to configure the author
	 */
	public void author(Action<Person> action) {
		create(Person.class, getAuthors(), action);
	}

	/**
	 * A list of contributors to the mod.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Person} representing the mod contributors
	 */
	@Input
	@Optional
	public abstract ListProperty<Person> getContributors();

	/**
	 * Add a new contributor with the given name.
	 *
	 * @param name The name of the contributor
	 */
	public void contributor(String name) {
		contributor(name, person -> { });
	}

	/**
	 * Add a new contributor with the given name, and configure it with the given action.
	 *
	 * @param name The name of the contributor
	 * @param action An action to configure the contributor further
	 */
	public void contributor(String name, Action<Person> action) {
		contributor(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	/**
	 * Add a new contributor, and configure it with the given action.
	 *
	 * @param action An action to configure the contributor
	 */
	public void contributor(Action<Person> action) {
		create(Person.class, getContributors(), action);
	}

	/**
	 * A map of contact information for the mod.
	 *
	 * <p>The key is the platform (e.g. "email", "github", "discord") and the value is the contact detail for that platform.
	 *
	 * @return A {@link MapProperty} containing a map of {@link String} keys and {@link String} values representing the mod contact information
	 */
	@Input
	@Optional
	public abstract MapProperty<String, String> getContactInformation();

	/**
	 * A list of icons for the mod.
	 *
	 * @return A {@link ListProperty} containing a list of {@link Icon} representing the mod icons
	 */
	@Input
	@Optional
	public abstract ListProperty<Icon> getIcons();

	/**
	 * Add a new icon with the given path.
	 *
	 * <p>Note: Only 1 unsized icon is allowed. If you need to specify multiple icons or sizes, use {@link #icon(int, String)}
	 *
	 * @param path The path to the icon file, relative to the root of the mod JAR
	 */
	public void icon(String path) {
		icon(path, icon -> { });
	}

	/**
	 * Add a new icon with the given size and path.
	 *
	 * @param size The size of the icon in pixels (e.g. 16, 32, 64)
	 * @param path The path to the icon file, relative to the root of the mod JAR
	 */
	public void icon(int size, String path) {
		icon(path, icon -> icon.getSize().set(size));
	}

	/**
	 * Add a new icon with the given path, and configure it with the given action.
	 *
	 * @param path The path to the icon file, relative to the root of the mod JAR
	 * @param action An action to configure the icon further
	 */
	public void icon(String path, Action<Icon> action) {
		icon(icon -> {
			icon.getPath().set(path);
			action.execute(icon);
		});
	}

	/**
	 * Add a new icon, and configure it with the given action.
	 *
	 * @param action An action to configure the icon
	 */
	public void icon(Action<Icon> action) {
		create(Icon.class, getIcons(), action);
	}

	/**
	 * A map of language adapters for the mod.
	 *
	 * <p>The key is the adapter name and the value is the fully qualified class name of the adapter.
	 *
	 * @return A {@link MapProperty} containing a map of {@link String} keys and {@link String} values representing the mod language adapters
	 */
	@Input
	@Optional
	public abstract MapProperty<String, String> getLanguageAdapters();

	/**
	 * A map of custom data for the mod.
	 *
	 * <p>This can be used by other tools to store additional information about the mod.
	 *
	 * <p>The object is encoded to JSON using Gson, so it can be any type that Gson supports.
	 *
	 * @return A {@link MapProperty} containing a map of {@link String} keys and {@link Object} values representing the mod custom data
	 */
	@Input
	@Optional
	public abstract MapProperty<String, Object> getCustomData();

	public abstract static class Entrypoint {
		/**
		 * The name of the entrypoint, such as "main" or "client".
		 *
		 * @return A {@link Property} containing a {@link String} representing the entrypoint name
		 */
		@Input
		public abstract Property<String> getEntrypoint();

		/**
		 * The value of the entrypoint, typically a fully qualified class name.
		 *
		 * @return A {@link Property} containing a {@link String} representing the entrypoint value
		 */
		@Input
		public abstract Property<String> getValue();

		/**
		 * The language adapter to use for this entrypoint, if any.
		 *
		 * @return A {@link Property} containing a {@link String} representing the entrypoint language adapter
		 */
		@Input
		@Optional
		public abstract Property<String> getAdapter();
	}

	public abstract static class Mixin {
		/**
		 * The value of the Mixin configuration, typically a path to a JSON file.
		 *
		 * @return A {@link Property} containing a {@link String} representing the Mixin configuration value
		 */
		@Input
		public abstract Property<String> getValue();

		/**
		 * The environment the Mixin configuration applies to.
		 *
		 * <p>One of `client`, `server`, or `*`.
		 *
		 * @return A {@link Property} containing a {@link String} representing the Mixin configuration environment
		 */
		@Input
		@Optional
		public abstract Property<String> getEnvironment();
	}

	public abstract static class Dependency {
		/**
		 * The mod ID of the dependency.
		 *
		 * @return A {@link Property} containing a {@link String} representing the dependency mod ID
		 */
		@Input
		public abstract Property<String> getModId();

		/**
		 * A list of version requirements for the dependency.
		 *
		 * <p>Each version requirement is a string that specifies a version or range of versions.
		 *
		 * @return A {@link ListProperty} containing a list of {@link String} representing the dependency version requirements
		 */
		@Input
		@Optional
		public abstract ListProperty<String> getVersionRequirements();
	}

	public abstract static class Person {
		/**
		 * The name of the person.
		 *
		 * @return A {@link Property} containing a {@link String} representing the person's name
		 */
		@Input
		public abstract Property<String> getName();

		/**
		 * A map of contact information for the person.
		 *
		 * <p>The key is the platform (e.g. "email", "github", "discord") and the value is the contact detail for that platform.
		 *
		 * @return A {@link MapProperty} containing a map of {@link String} keys and {@link String} values representing the person's contact information
		 */
		@Input
		@Optional
		public abstract MapProperty<String, String> getContactInformation();
	}

	public abstract static class Icon {
		/**
		 * The path to the icon file, relative to the root of the mod JAR.
		 *
		 * @return A {@link Property} containing a {@link String} representing the icon path
		 */
		@Input
		public abstract Property<String> getPath();

		/**
		 * The size of the icon in pixels (e.g. 16, 32, 64).
		 *
		 * <p>If not specified, the icon is considered to be "unsized". Only one unsized icon is allowed.
		 *
		 * @return A {@link Property} containing an {@link Integer} representing the icon size
		 */
		@Input
		@Optional // Icon is required if there is more than 1 icon specified
		public abstract Property<Integer> getSize();
	}

	// Internal stuff:

	@Inject
	@ApiStatus.Internal
	protected abstract ObjectFactory getObjectFactory();

	private <T> void create(Class<T> type, ListProperty<T> list, Action<T> action) {
		T item = getObjectFactory().newInstance(type);
		action.execute(item);
		list.add(item);
	}
}
