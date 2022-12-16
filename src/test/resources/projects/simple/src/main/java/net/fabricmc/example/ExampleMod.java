package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExampleMod implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("modid");

	/**
	 * @see net.fabricmc.example
	 */
	@Override
	public void onInitialize() {
		LOGGER.info("Hello simple Fabric mod!");
	}
}