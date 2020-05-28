package net.fabricmc.loom.decompilers.fernflower;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.decompilers.DecompilationMetadata;
import net.fabricmc.loom.decompilers.LoomDecompiler;
import net.fabricmc.loom.util.ConsumingOutputStream;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.stitch.util.StitchUtil;

public abstract class AbstractFernFlowerDecompiler implements LoomDecompiler {
	private final Project project;

	protected AbstractFernFlowerDecompiler(Project project) {
		this.project = project;
	}

	public abstract Class<? extends AbstractForkedFFExecutor> fernFlowerExecutor();

	@Override
	public void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData) {
		if (!OperatingSystem.is64Bit()) {
			throw new UnsupportedOperationException("FernFlower decompiler requires a 64bit JVM to run due to the memory requirements");
		}

		project.getLogging().captureStandardOutput(LogLevel.LIFECYCLE);

		Map<String, Object> options = new HashMap<String, Object>() {{
			put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
			put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
			put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
			put(IFernflowerPreferences.LOG_LEVEL, "trace");
			put(IFernflowerPreferences.THREADS, metaData.numberOfThreads);
		}};


		List<String> args = new ArrayList<>();

		options.forEach((k, v) -> args.add(format("-{0}={1}", k, v)));
		args.add(absolutePathOf(compiledJar));
		args.add("-o=" + absolutePathOf(sourcesDestination));
		args.add("-l=" + absolutePathOf(linemapDestination));
		args.add("-m=" + absolutePathOf(metaData.javaDocs));

		//TODO, Decompiler breaks on jemalloc, J9 module-info.class?
		for (Path library : metaData.libraries) args.add("-e=" + absolutePathOf(library));

		ServiceRegistry registry = ((ProjectInternal) project).getServices();
		ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
		ProgressLogger progressGroup = factory.newOperation(getClass()).setDescription("Decompile");
		Supplier<ProgressLogger> loggerFactory = () -> {
			ProgressLogger pl = factory.newOperation(getClass(), progressGroup);
			pl.setDescription("decompile worker");
			pl.started();
			return pl;
		};
		Stack<ProgressLogger> freeLoggers = new Stack<>();
		Map<String, ProgressLogger> inUseLoggers = new HashMap<>();

		progressGroup.started();
		ExecResult result = ForkingJavaExec.javaexec(project, spec -> {
			spec.setMain(fernFlowerExecutor().getName());
			spec.jvmArgs("-Xms200m", "-Xmx3G");
			spec.setArgs(args);
			spec.setErrorOutput(System.err);
			spec.setStandardOutput(new ConsumingOutputStream(line -> {
				if (line.startsWith("Listening for transport") || !line.contains("::")) {
					System.out.println(line);
					return;
				}

				int sepIdx = line.indexOf("::");
				String id = line.substring(0, sepIdx).trim();
				String data = line.substring(sepIdx + 2).trim();

				ProgressLogger logger = inUseLoggers.get(id);

				String[] segs = data.split(" ");

				if (segs[0].equals("waiting")) {
					if (logger != null) {
						logger.progress("Idle..");
						inUseLoggers.remove(id);
						freeLoggers.push(logger);
					}
				} else {
					if (logger == null) {
						if (!freeLoggers.isEmpty()) {
							logger = freeLoggers.pop();
						} else {
							logger = loggerFactory.get();
						}

						inUseLoggers.put(id, logger);
					}

					logger.progress(data);
				}
			}));
		});
		inUseLoggers.values().forEach(ProgressLogger::completed);
		freeLoggers.forEach(ProgressLogger::completed);
		progressGroup.completed();

		result.rethrowFailure();
		result.assertNormalExitValue();
	}

//	@Override
//	public void generateSources(Path compiledJar, Path destination, DecompilationMetadata metaData) {
//		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
//		try {
//			Path unlinemappedJar = LoomGradlePlugin.getMappedByproduct(project, "unlinemapped-sources.jar").toPath();
//			Path linemap = Files.createTempFile(extension.getUserCache().toPath(), "unlinemappedJar", ".lmap");
//
//			decompile(compiledJar, unlinemappedJar, linemap, metaData);
//			remapLineNumbers(unlinemappedJar, linemap, destination);
//
//		} catch (IOException e) {
//			project.getLogger().error("Could not generate sources", e);
//		}
//	}
//
//	private void decompile(Path compiledJar, Path unlinemappedDestination, Path linemapDestination, DecompilationMetadata metaData) {
//
//	}



	private static String absolutePathOf(Path path) {
		return path.toAbsolutePath().toString();
	}
}
