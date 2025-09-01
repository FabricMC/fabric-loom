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

import java.util.Collection;

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

	public void entrypoint(String entrypoint, String value) {
		entrypoint(entrypoint, value, metadata -> { });
	}

	public void entrypoint(String entrypoint, String value, Action<Entrypoint> action) {
		entrypoint(entrypoint, metadata -> {
			metadata.getValue().set(value);
			action.execute(metadata);
		});
	}

	public void entrypoint(String entrypoint, Action<Entrypoint> action) {
		create(Entrypoint.class, getEntrypoints(), e -> {
			e.getEntrypoint().set(entrypoint);
			action.execute(e);
		});
	}

	@Input
	@Optional
	public abstract ListProperty<String> getNestedJars();

	@Input
	@Optional
	public abstract ListProperty<Mixin> getMixins();

	public void mixin(String value) {
		mixin(value, mixin -> { });
	}

	public void mixin(String value, Action<Mixin> action) {
		mixin(mixin -> {
			mixin.getValue().set(value);
			action.execute(mixin);
		});
	}

	public void mixin(Action<Mixin> action) {
		create(Mixin.class, getMixins(), action);
	}

	@Input
	@Optional
	public abstract Property<String> getAccessWidener();

	@Input
	@Optional
	public abstract ListProperty<Dependency> getDepends();

	public void depends(String modId, Iterable<String> versionRequirements) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	public void depends(String modId, String versionRequirement) {
		depends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	public void depends(String modId, Action<Dependency> action) {
		depends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	public void depends(Action<Dependency> action) {
		create(Dependency.class, getDepends(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getRecommends();

	public void recommends(String modId, Iterable<String> versionRequirements) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	public void recommends(String modId, String versionRequirement) {
		recommends(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	public void recommends(String modId, Action<Dependency> action) {
		recommends(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	public void recommends(Action<Dependency> action) {
		create(Dependency.class, getRecommends(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getSuggests();

	public void suggests(String modId, Iterable<String> versionRequirements) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	public void suggests(String modId, String versionRequirement) {
		suggests(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	public void suggests(String modId, Action<Dependency> action) {
		suggests(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	public void suggests(Action<Dependency> action) {
		create(Dependency.class, getSuggests(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getConflicts();

	public void conflicts(String modId, Iterable<String> versionRequirements) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	public void conflicts(String modId, String versionRequirement) {
		conflicts(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	public void conflicts(String modId, Action<Dependency> action) {
		conflicts(dependency -> {
			dependency.getModId().set(modId);
			action.execute(dependency);
		});
	}

	public void conflicts(Action<Dependency> action) {
		create(Dependency.class, getConflicts(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Dependency> getBreaks();

	public void breaks(String modId, Iterable<String> versionRequirements) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().addAll(versionRequirements);
		});
	}

	public void breaks(String modId, String versionRequirement) {
		breaks(modId, dependency -> {
			dependency.getVersionRequirements().add(versionRequirement);
		});
	}

	public void breaks(String modId, Action<Dependency> action) {
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

	public void author(String name) {
		author(name, person -> { });
	}

	public void author(String name, Action<Person> action) {
		author(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	public void author(Action<Person> action) {
		create(Person.class, getAuthors(), action);
	}

	@Input
	@Optional
	public abstract ListProperty<Person> getContributors();

	public void contributor(String name) {
		contributor(name, person -> { });
	}

	public void contributor(String name, Action<Person> action) {
		contributor(person -> {
			person.getName().set(name);
			action.execute(person);
		});
	}

	public void contributor(Action<Person> action) {
		create(Person.class, getContributors(), action);
	}

	@Input
	@Optional
	public abstract MapProperty<String, String> getContactInformation();

	@Input
	@Optional
	public abstract ListProperty<Icon> getIcons();

	public void icon(String path) {
		icon(path, icon -> { });
	}

	public void icon(String path, Action<Icon> action) {
		icon(icon -> {
			icon.getPath().set(path);
			action.execute(icon);
		});
	}

	public void icon(Action<Icon> action) {
		create(Icon.class, getIcons(), action);
	}

	@Input
	@Optional
	public abstract MapProperty<String, String> getLanguageAdapters();

	// TODO custom data

	public abstract static class Entrypoint {
		@Input
		public abstract Property<String> getEntrypoint();

		@Input
		public abstract Property<String> getValue();

		@Input
		@Optional
		public abstract Property<String> getAdapter();
	}

	public abstract static class Mixin {
		@Input
		public abstract Property<String> getValue();

		@Input
		@Optional
		public abstract Property<String> getEnvironment();
	}

	public abstract static class Dependency {
		@Input
		public abstract Property<String> getModId();

		@Input
		@Optional
		public abstract ListProperty<String> getVersionRequirements();
	}

	public abstract static class Person {
		@Input
		public abstract Property<String> getName();

		@Input
		@Optional
		public abstract MapProperty<String, String> getContactInformation();
	}

	public abstract static class Icon {
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

	private <T> void create(Class<T> type, Collection<T> list, Action<T> action) {
		T item = getObjectFactory().newInstance(type);
		action.execute(item);
		list.add(item);
	}
}
