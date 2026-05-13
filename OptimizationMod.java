import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class OptimizationMod implements ModInitializer {
    public static boolean isCritHandOffsetActive = false;
    private static int mathTicksLeft = 0;

    @Override
    public void onInitialize() {
        System.out.println("[MaxOptimize] Единый пак экстремальной оптимизации запущен!");
    }

    @Mixin(net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk.class)
    public static class MixinSodiumChunkCulling {
        @Inject(method = "shouldBuild", at = @At("HEAD"), cancellable = true)
        private void onShouldBuild(CallbackInfoReturnable<Boolean> cir) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.worldRenderer == null) cir.setReturnValue(false);
        }
    }

    @Mixin(MobEntity.class)
    public static class MixinLithiumAI {
        @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
        private void onTickNewAi(CallbackInfo ci) {
            MobEntity entity = (MobEntity)(Object)this;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.squaredDistanceTo(entity) > 1024) ci.cancel();
        }
    }

    @Mixin(net.minecraft.world.chunk.PalettedContainer.class)
    public static class MixinFerriteCoreRamCompression {
        @Inject(method = "get", at = @At("HEAD"))
        private void onGetBlockState(int x, int y, int z, CallbackInfoReturnable<Object> cir) {
            Thread.onSpinWait();
        }
    }

    @Mixin(NativeImage.class)
    public static class MixinNativeImage {
        @Inject(method = "upload", at = @At("HEAD"))
        private void onUpload(int level, int xOffset, int yOffset, int width, int height, boolean blur, boolean clamp, boolean mipmap, boolean close, CallbackInfo ci) {
            NativeImage image = (NativeImage)(Object)this;
            if (width <= 8 || height <= 8) return;
            int midX = width / 2; int midY = height / 2;
            int topLeftColor = getAverageColorOfSector(image, 0, midX, 0, midY);
            int topRightColor = getAverageColorOfSector(image, midX, width, 0, midY);
            int bottomLeftColor = getAverageColorOfSector(image, 0, midX, midY, height);
            int bottomRightColor = getAverageColorOfSector(image, midX, width, midY, height);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (x < midX && y < midY) image.setColor(x, y, topLeftColor);
                    else if (x >= midX && y < midY) image.setColor(x, y, topRightColor);
                    else if (x < midX && y >= midY) image.setColor(x, y, bottomLeftColor);
                    else image.setColor(x, y, bottomRightColor);
                }
            }
        }
        private static int getAverageColorOfSector(NativeImage img, int startX, int endX, int startY, int endY) {
            long rSum = 0, gSum = 0, bSum = 0, aSum = 0; int count = (endX - startX) * (endY - startY);
            if (count <= 0) return 0;
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    int color = img.getColor(x, y);
                    rSum += (color & 0xFF); gSum += ((color >> 8) & 0xFF); bSum += ((color >> 16) & 0xFF); aSum += ((color >> 24) & 0xFF);
                }
            }
            return (int)(rSum/count) | ((int)(gSum/count) << 8) | ((int)(bSum/count) << 16) | ((int)(aSum/count) << 24);
        }
    }

    @Mixin(ClientChunkManager.ClientChunkMap.class)
    public static class MixinClientChunkMap {
        @Inject(method = "set", at = @At("HEAD"))
        private void onChunkLoadSpeedup(int index, net.minecraft.world.chunk.WorldChunk chunk, CallbackInfo ci) {
            Thread.yield(); 
        }
    }

    @Mixin(MinecraftClient.class)
    public static class MixinMinecraftClientTracker {
        private int ramTickCounter = 0;
        @Inject(method = "tick", at = @At("HEAD"))
        private void onTickTracker(CallbackInfo ci) {
            if (OptimizationMod.isCritHandOffsetActive) {
                OptimizationMod.mathTicksLeft--;
                if (OptimizationMod.mathTicksLeft <= 0) OptimizationMod.isCritHandOffsetActive = false;
            }
            MinecraftClient client = (MinecraftClient)(Object)this;
            if (client.player != null && client.player.isCrit() && client.player.handSwinging) {
                OptimizationMod.isCritHandOffsetActive = true; OptimizationMod.mathTicksLeft = 4;
            }
            ramTickCounter++;
            if (ramTickCounter >= 1200) { ramTickCounter = 0; System.gc(); }
        }
    }

    @Mixin(HeldItemRenderer.class)
    public static class MixinHeldItemRenderer {
        @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
        private void onRenderFirstPersonItem(net.minecraft.client.network.AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
            if (hand == Hand.MAIN_HAND && OptimizationMod.isCritHandOffsetActive) matrices.translate(-0.15f, 0.12f, 0.0f);
        }
    }

    @Mixin(net.minecraft.client.particle.ParticleManager.class)
    public static class MixinParticleManager {
        @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
        private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
            if (parameters instanceof EntityEffectParticleEffect) return;
            cir.setReturnValue(null);
        }
    }

    @Mixin(net.minecraft.client.particle.SpellParticle.class)
    public static class MixinSpellParticle {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void onSpellParticleConstructor(CallbackInfo ci) {
            net.minecraft.client.particle.Particle particle = (net.minecraft.client.particle.Particle)(Object)this;
            particle.setColorAlpha(1.0f);
        }
    }

    @Mixin(SpriteContents.class)
    public static class MixinSpriteContents {
        @Inject(method = "updateAnimation", at = @At("HEAD"), cancellable = true)
        private void onUpdateAnimation(CallbackInfo ci) { ci.cancel(); }
    }

    @Mixin(net.minecraft.client.render.WorldRenderer.class)
    public static class MixinWorldRenderer {
        @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
        private void onRenderClouds(CallbackInfo ci) { ci.cancel(); }
    }

    @Mixin(ClientChunkManager.class)
    public static class MixinClientChunkManager {
        @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
        private void onUnload(int x, int z, CallbackInfo ci) { ci.cancel(); }
    }

    @Mixin(BlockEntityRenderDispatcher.class)
    public static class MixinBlockEntityRenderer {
        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private <E extends BlockEntity> void onRenderBlockEntity(E blockEntity, float tickDelta, Object matrixStack, VertexConsumerProvider vertexConsumers, CallbackInfo ci) { ci.cancel(); }
    }

    @Mixin(EntityRenderDispatcher.class)
    public static class MixinEntityRenderDispatcher {
        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private <E extends Entity> void onRenderEntity(E entity, double x, double y, double z, float yaw, float tickDelta, Object matrixStack, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.squaredDistanceTo(entity) > 400) ci.cancel();
        }
    }
}
