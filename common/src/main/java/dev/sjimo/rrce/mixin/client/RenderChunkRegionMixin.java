package dev.sjimo.rrce.mixin.client;

import dev.sjimo.rrce.client.RrceClient;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the client chunk mesh sees air where a complete-work preview replaces a block. */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void rrce$hideExistingPreviewBlock(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        if (RrceClient.shouldHideExistingBlock(pos)) ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void rrce$hideExistingPreviewFluid(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
        if (RrceClient.shouldHideExistingBlock(pos)) ci.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }
}
