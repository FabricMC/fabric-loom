package net.fabricmc.example.mixin;

import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public abstract class ExampleMixin<T> {
	@Shadow
	private volatile PalettedContainer.Data<T> data;
}