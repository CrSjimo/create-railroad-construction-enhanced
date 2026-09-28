package dev.sjimo.rrce;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Removes the selected point; the target block is irrelevant. */
public final class RemovePointTool extends ControlPointTool {
    public RemovePointTool(Properties properties) { super(properties); }

    @Override
    protected void perform(ServerPlayer player, Level level, BlockPos clicked, Direction face) {
        PlayerSession session = SessionManager.get(player.getUUID());
        session.removeSelected();
        player.sendSystemMessage(Component.translatable("message.rrce.point_removed", session.points.size()));
    }

    @Override
    protected boolean removesPoint() { return true; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(useAt(player, level, player.blockPosition(), Direction.UP),
            player.getItemInHand(hand));
    }
}
