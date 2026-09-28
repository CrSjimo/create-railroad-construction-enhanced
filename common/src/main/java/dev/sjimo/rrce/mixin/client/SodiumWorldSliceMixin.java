package dev.sjimo.rrce.mixin.client;

import dev.sjimo.rrce.client.RrceClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium's chunk mesh reads from WorldSlice rather than RenderChunkRegion. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class SodiumWorldSliceMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true, remap = false)
    private void rrce$hideBlockByCoordinates(int x, int y, int z, CallbackInfoReturnable<BlockState> ci) {
        if (RrceClient.shouldHideExistingBlock(x, y, z)) ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "method_8320", at = @At("HEAD"), cancellable = true, remap = false)
    private void rrce$hideBlockByPosition(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        if (RrceClient.shouldHideExistingBlock(pos)) ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "method_8316", at = @At("HEAD"), cancellable = true, remap = false)
    private void rrce$hideFluid(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
        if (RrceClient.shouldHideExistingBlock(pos)) ci.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }
}
