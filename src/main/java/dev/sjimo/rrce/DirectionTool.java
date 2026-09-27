package dev.sjimo.rrce;

import java.util.List;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper.Palette;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Rotates the selected rail tangent by one 45-degree step. */
public final class DirectionTool extends Item {
    public DirectionTool(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = rotate(player, level);
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    public InteractionResult rotate(Player player, Level level) {
        if (player.isSpectator()) return InteractionResult.FAIL;
        if (!ToolPress.claim(player, level)) return InteractionResult.CONSUME;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        PlayerSession session = SessionManager.get(player.getUUID());
        if (session.selectedPoint < 0 || session.selectedPoint >= session.points.size()) {
            RrceSounds.deny(serverPlayer);
            player.sendSystemMessage(Component.translatable("message.rrce.no_selected_point"));
            return InteractionResult.CONSUME;
        }
        session.rotateSelected(player.isShiftKeyDown() ? -1 : 1);
        var point = session.points.get(session.selectedPoint);
        player.sendSystemMessage(Component.translatable("message.rrce.point_facing",
            session.selectedPoint + 1, point.heading().displayName()));
        RrceMod.syncPointEdit(serverPlayer, false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemDescription description = ItemDescription.create(this, Palette.STANDARD_CREATE);
        if (description != null) tooltip.addAll(description.getCurrentLines());
    }
}
