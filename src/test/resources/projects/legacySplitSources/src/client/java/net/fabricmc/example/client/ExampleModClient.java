package net.fabricmc.example.client;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(ExampleModClient.class);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Hello Client");

		// Check we can compile against the client.
		MinecraftClient client;
	}
}
