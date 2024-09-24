package net.fabricmc.loom.test.unit.processor.classes;

public class DoublePassingGenericTargetClass<F, S> {
	public static class Pair<F, S> {
		Pair(F ignoredF, S ignoredS) {
		}
	}
}
