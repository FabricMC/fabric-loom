package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.BlockState;
import techreborn.blocks.cable.CableShapeUtil;
import net.minecraft.util.shape.VoxelShape;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		System.out.println("Hello Fabric world!");

		if (false) {
			// Just here to make sure it compiles as named, not to test it runs
			BlockState state = null;
			VoxelShape shape = new CableShapeUtil(null).getShape(state);
		}
	}
}
