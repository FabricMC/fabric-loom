package net.fabricmc.loom.test.unit.processor.classes;

public interface DoublePassingGenericInterface<F, S> {
	default DoublePassingGenericTargetClass.Pair<F, S> doublePassingGenericInjectedMethod() {
		return new DoublePassingGenericTargetClass.Pair<>(null, null);
	}
}
