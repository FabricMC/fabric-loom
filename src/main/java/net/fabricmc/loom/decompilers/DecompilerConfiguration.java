package net.fabricmc.loom.decompilers;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.decompilers.cfr.FabricCFRDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;

public final class DecompilerConfiguration {
	private DecompilerConfiguration() {
	}

	public static void setup(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		extension.addDecompiler(new FabricFernFlowerDecompiler(project));
		extension.addDecompiler(new FabricCFRDecompiler(project));
	}
}
