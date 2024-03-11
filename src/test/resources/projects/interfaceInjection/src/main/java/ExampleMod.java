import net.minecraft.block.Blocks;
import net.minecraft.util.registry.Registry;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Blocks.AIR.newMethodThatDidNotExist();
		Blocks.AIR.anotherNewMethodThatDidNotExist();
		Blocks.AIR.typedMethodThatDidNotExist();
		Registry.BLOCK_KEY.genericMethodThatDidNotExist();
	}
}
