package net.fabricmc.example.test;

import net.fabricmc.example.client.ExampleModClient;

import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.Test;

public class ExampleModTest {
	@Test
	void testClientClass() {
		// Check we can compile against our own client code
		ExampleModClient exampleModClient = new ExampleModClient();

		// And minecrafts client code
		MinecraftClient client = null;
	}
}
