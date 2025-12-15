import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Registry;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Blocks.AIR.newMethodThatDidNotExist();
		Blocks.AIR.anotherNewMethodThatDidNotExist();
		Blocks.AIR.typedMethodThatDidNotExist();
		Registry.BLOCK_REGISTRY.genericMethodThatDidNotExist();
	}
}
