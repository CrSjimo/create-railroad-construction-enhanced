package dev.sjimo.rrce.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sjimo.rrce.client.RrceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rrce$hideExistingPreviewBlockEntity(BlockEntity entity, float partialTick, PoseStack pose,
                                                       MultiBufferSource buffers, CallbackInfo ci) {
        if (entity.getLevel() == Minecraft.getInstance().level
            && RrceClient.shouldHideExistingBlock(entity.getBlockPos())) ci.cancel();
    }
}
