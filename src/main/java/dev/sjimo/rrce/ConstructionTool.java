package dev.sjimo.rrce;

import com.simibubi.create.content.trains.track.TrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Inserts a new control point directly after the selected point. */
public final class ConstructionTool extends ControlPointTool {
    public ConstructionTool(Properties properties) { super(properties); }

    @Override
    protected void perform(ServerPlayer player, Level level, BlockPos clicked, Direction face) {
        PlayerSession session = SessionManager.get(player.getUUID());
        BlockPos point = ControlPointPlacement.position(level, clicked, face, session.config);
        RrceMod.validatePointPosition(player, point);
        var heading = ControlPointPlacement.heading(level, clicked, player);
        session.addPoint(point, heading);
        player.sendSystemMessage(Component.translatable("message.rrce.point_added",
            session.selectedPoint + 1, Component.literal(point.toShortString() + " · ").append(heading.displayName())));
        if (!point.equals(level.getBlockState(clicked).getBlock() instanceof TrackBlock
            ? clicked : clicked.relative(face)))
            player.sendSystemMessage(Component.translatable("message.rrce.point_aligned"));
    }
}
