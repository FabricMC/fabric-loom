package net.fabricmc.example.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.gui.screen.TitleScreen;

// Just a simple class to ensure the tests can compile against loader and minecraft
public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		TitleScreen screen = null;
	}
}
