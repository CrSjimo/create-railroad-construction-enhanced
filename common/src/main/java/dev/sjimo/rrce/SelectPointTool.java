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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Selects a point in the clicked block, cycling through co-located points. */
public final class SelectPointTool extends ControlPointTool {
    public SelectPointTool(Properties properties) { super(properties); }

    @Override
    protected void perform(ServerPlayer player, Level level, BlockPos clicked, Direction face) {
        PlayerSession session = SessionManager.get(player.getUUID());
        BlockPos target = session.points.stream().anyMatch(point -> point.pos().equals(clicked))
            ? clicked : viewedPoint(player, session);
        if (target == null) throw new UserFacingException("error.rrce.no_point_here");
        int index = session.selectNextAt(target);
        RrceSounds.rotate(player);
        player.sendSystemMessage(Component.translatable("message.rrce.point_selected", index + 1));
    }

    @Override
    protected boolean changesRoute() { return false; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(useAt(player, level, player.blockPosition(), Direction.UP),
            player.getItemInHand(hand));
    }

    private static BlockPos viewedPoint(ServerPlayer player, PlayerSession session) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(5));
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (var point : session.points) {
            var hit = new AABB(point.pos()).inflate(.12).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point.pos();
            }
        }
        return nearest;
    }
}
