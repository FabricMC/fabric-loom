import net.minecraft.client.MinecraftClient;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MinecraftClient.getInstance().newMethodThatDidNotExist();
    }
}
