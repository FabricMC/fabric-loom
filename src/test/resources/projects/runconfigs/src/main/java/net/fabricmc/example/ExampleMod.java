package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("Hello Fabric world!");

		System.out.println(System.getProperty("fabric.loom.test.space"));

		// Quit now, we dont need to load the whole game to know the run configs are works
		System.exit(0);
	}
}
