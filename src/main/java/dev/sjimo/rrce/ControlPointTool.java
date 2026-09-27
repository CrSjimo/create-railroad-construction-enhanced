package dev.sjimo.rrce;

import java.util.List;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper.Palette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

abstract class ControlPointTool extends Item {
    ControlPointTool(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return useAt(context.getPlayer(), context.getLevel(), context.getClickedPos(), context.getClickedFace());
    }

    public final InteractionResult useAt(Player usingPlayer, Level level, BlockPos clicked, Direction face) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(usingPlayer instanceof ServerPlayer player) || player.isSpectator()) return InteractionResult.FAIL;
        try {
            perform(player, level, clicked, face);
            if (changesRoute()) RrceMod.syncPointEdit(player, removesPoint());
            else RrceMod.sync(player);
        } catch (RuntimeException error) {
            RrceSounds.deny(player);
            if (!(error instanceof UserFacingException)) RrceMod.LOGGER.warn("Control point action failed", error);
            player.sendSystemMessage(Component.translatable("message.rrce.operation_failed",
                UserFacingException.display(error)));
        }
        return InteractionResult.CONSUME;
    }

    protected abstract void perform(ServerPlayer player, Level level, BlockPos clicked, Direction face);

    protected boolean changesRoute() { return true; }
    protected boolean removesPoint() { return false; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipLines, TooltipFlag flag) {
        ItemDescription description = ItemDescription.create(this, Palette.STANDARD_CREATE);
        if (description != null) tooltipLines.addAll(description.getCurrentLines());
    }
}
