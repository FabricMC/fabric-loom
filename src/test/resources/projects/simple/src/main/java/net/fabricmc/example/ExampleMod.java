package net.fabricmc.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("modid");

	/**
	 * @see net.fabricmc.example
	 */
	@Override
	public void onInitialize() {
		LOGGER.info("Hello simple Fabric mod!");
		LOGGER.info("Hello World!");
	}
}
