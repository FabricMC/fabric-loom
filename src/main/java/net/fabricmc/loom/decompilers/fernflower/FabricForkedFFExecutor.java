package net.fabricmc.loom.decompilers.fernflower;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

public class FabricForkedFFExecutor extends AbstractForkedFFExecutor {
	public static void main(String[] args) {
		AbstractForkedFFExecutor.decompile(args, new FabricForkedFFExecutor());
	}
	@Override
	void runFF(Map<String, Object> options, List<File> libraries, File input, File output, File lineMap) {
		IResultSaver saver = new ThreadSafeResultSaver(() -> output, () -> lineMap);
		IFernflowerLogger logger = new ThreadIDFFLogger();
		Fernflower ff = new Fernflower(FernFlowerUtils::getBytecode, saver, options, logger);

		for (File library : libraries) {
			ff.addLibrary(library);
		}

		ff.addSource(input);
		ff.decompileContext();
	}
}
