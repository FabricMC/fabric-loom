package net.fabricmc.loom.decompilers.fernflower;

import org.gradle.api.Project;

public class FabricFernFlowerDecompiler extends AbstractFernFlowerDecompiler {
	public FabricFernFlowerDecompiler(Project project) {
		super(project);
	}

	@Override
	public String name() {
		return "FabricFlower"; // Or something else?
	}

	@Override
	public Class<? extends AbstractForkedFFExecutor> fernFlowerExecutor() {
		return FabricForkedFFExecutor.class;
	}
}
