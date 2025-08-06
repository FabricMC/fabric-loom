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
	@Input
	public abstract Property<String> getModId();

	@Input
	public abstract Property<String> getVersion();

	@Input
	@Optional
	public abstract Property<String> getName();

	@Input
	@Optional
	public abstract Property<String> getDescription();

	@Input
	@Optional
	public abstract ListProperty<String> getProvides();

	// One of `client`, `server`, or `*`.
	@Input
	@Optional
	public abstract Property<String> getEnvironment();

	public void client() {
		getEnvironment().set("client");
	}

	public void server() {
		getEnvironment().set("server");
	}

	@Input
	@Optional
	public abstract ListProperty<Entrypoint> getEntrypoints();

	void entrypoint(String value) {
		entrypoint(value, entrypoint -> {});
	}

	void entrypoint(String value, Action<Entrypoint> action) {
		entrypoint(entrypoint -> {
			entrypoint.getValue().set(value);
			action.execute(entrypoint);
		});
	}

	void entrypoint(Action<Entrypoint> action) {
		create(Entrypoint.class, getEntrypoints(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<String> getNestedJars();

	@Input
	@Optional
	public abstract ListProperty<Mixin> getMixins();

	void mixin(String value) {
		mixin(value, mixin -> {});
	}

	void mixin(String value, Action<Mixin> action) {
		mixin(mixin -> {
			mixin.getValue().set(value);
			action.execute(mixin);
		});
	}

	void mixin(Action<Mixin> action) {
		create(Mixin.class, getMixins(), action);
	}

	@Input
	@Optional
	public abstract Property<String> getAccessWidener();

	@Input
	@Optional
	public abstract ListProperty<Dependency> getDepends();

	void depends(String modId, Iterable<String> versionRequirements) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	void depends(String modId, String versionRequirement) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	void depends(String modId, Action<Dependency> action) {
		depends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	void depends(Action<Dependency> action) {
		create(Dependency.class, getDepends(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getRecommends();

	void recommends(String modId, Iterable<String> versionRequirements) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	void recommends(String modId, String versionRequirement) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	void recommends(String modId, Action<Dependency> action) {
		recommends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	void recommends(Action<Dependency> action) {
		create(Dependency.class, getRecommends(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getSuggests();

	void suggests(String modId, Iterable<String> versionRequirements) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	void suggests(String modId, String versionRequirement) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	void suggests(String modId, Action<Dependency> action) {
		suggests(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	void suggests(Action<Dependency> action) {
		create(Dependency.class, getSuggests(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getConflicts();

	void conflicts(String modId, Iterable<String> versionRequirements) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	void conflicts(String modId, String versionRequirement) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	void conflicts(String modId, Action<Dependency> action) {
		conflicts(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	void conflicts(Action<Dependency> action) {
		create(Dependency.class, getConflicts(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getBreaks();

	void breaks(String modId, Iterable<String> versionRequirements) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	void breaks(String modId, String versionRequirement) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	void breaks(String modId, Action<Dependency> action) {
		breaks(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	void breaks(Action<Dependency> action) {
		create(Dependency.class, getBreaks(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<String> getLicenses();

	@Input
	@Optional
	public abstract ListProperty<Person> getAuthors();

	void author(String name) {
		author(name, person -> {});
	}

	void author(String name, Action<Person> action) {
		author(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	void author(Action<Person> action) {
		create(Person.class, getAuthors(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Person> getContributors();

	void contributor(String name) {
		contributor(name, person -> {});
	}

	void contributor(String name, Action<Person> action) {
		contributor(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	void contributor(Action<Person> action) {
		create(Person.class, getContributors(), action);
	}

	@Input
	@Optional
	public abstract MapProperty<String, String> getContactInformation();

	@Input
	@Optional
	public abstract ListProperty<Icon> getIcons();

	void icon(String path) {
		icon(path, icon -> {});
	}

	void icon(String path, Action<Icon> action) {
		icon(icon -> {
			icon.getPath().set(path);
			action.execute(icon);
		});
	}

	void icon(Action<Icon> action) {
		create(Icon.class, getIcons(), action);
	}

	@Input
	@Optional
	public abstract MapProperty<String, String> getLanguageAdapters();

	// TODO custom data

	public static abstract class Entrypoint {
		@Input
		public abstract Property<String> getValue();

		@Input
		@Optional
		public abstract Property<String> getAdapter();
	}

	public static abstract class Mixin {
		@Input
		public abstract Property<String> getValue();

		@Input
		@Optional
		public abstract Property<String> getEnvironment();
	}

	public static abstract class Dependency {
		@Input
		public abstract Property<String> getModId();

		@Input
		@Optional
		public abstract ListProperty<String> getVersionRequirements();
	}

	public static abstract class Person {
		@Input
		public abstract Property<String> getName();

		@Input
		@Optional
		public abstract MapProperty<String, String> getContactInformation();
	}

	public static abstract class Icon {
		@Input
		public abstract Property<String> getPath();

		@Input
		@Optional
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
