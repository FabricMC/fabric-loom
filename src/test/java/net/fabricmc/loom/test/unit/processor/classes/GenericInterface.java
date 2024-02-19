package net.fabricmc.loom.test.unit.processor.classes;

public interface GenericInterface<T> {

	default T genericInjectedMethod() {
		return null;
	}
}
