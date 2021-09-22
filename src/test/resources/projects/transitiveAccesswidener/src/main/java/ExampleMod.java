import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.registry.RegistryKey;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// BiomeKeys.register has been made public by a transitive AW
		RegistryKey<Biome> biomeRegistryKey = BiomeKeys.register("dummy");
	}
}
