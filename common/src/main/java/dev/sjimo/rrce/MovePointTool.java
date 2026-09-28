package dev.sjimo.rrce;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Moves the selected point to the block chosen by the player. */
public final class MovePointTool extends ControlPointTool {
    public MovePointTool(Properties properties) { super(properties); }

    @Override
    protected void perform(ServerPlayer player, Level level, BlockPos clicked, Direction face) {
        PlayerSession session = SessionManager.get(player.getUUID());
        BlockPos point = ControlPointPlacement.position(level, clicked, face, session.config);
        RrceMod.validatePointPosition(player, point);
        session.moveSelected(point);
        player.sendSystemMessage(Component.translatable("message.rrce.point_moved",
            session.selectedPoint + 1, point.toShortString()));
    }
}
