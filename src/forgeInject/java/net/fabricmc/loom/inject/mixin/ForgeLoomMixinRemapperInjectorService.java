package net.fabricmc.loom.inject.mixin;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ForgeLoomMixinRemapperInjectorService implements ITransformationService {
	private static final Logger LOGGER = LogManager.getLogger("ForgeLoomRemapperInjector");

	@Nonnull
	@Override
	public String name() {
		return "ForgeLoomMixinRemapperInjector";
	}

	@Override
	public void initialize(IEnvironment environment) {

	}

	@Override
	public void beginScanning(IEnvironment environment) {
		LOGGER.debug("We will be injecting our remapper.");
		try {
			MixinEnvironment.getDefaultEnvironment().getRemappers().add(new MixinIntermediaryDevRemapper(Objects.requireNonNull(resolveMappings()), "intermediary", "named"));
			LOGGER.debug("We have successfully injected our remapper.");
		} catch (Exception e) {
			LOGGER.debug("We have failed to inject our remapper.", e);
		}
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

	}

	@Nonnull
	@Override
	public List<ITransformer> transformers() {
		return Collections.emptyList();
	}

	private static TinyTree resolveMappings() {
		try {
			String srgNamedProperty = System.getProperty("mixin.forgeloom.inject.mappings.srg-named");
			Path path = Paths.get(srgNamedProperty);
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				return TinyMappingFactory.loadWithDetection(reader);
			}
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			return null;
		}
	}
}
