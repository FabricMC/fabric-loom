import net.minecraft.block.Blocks;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Blocks.AIR.newMethodThatDidNotExist();
	}
}
