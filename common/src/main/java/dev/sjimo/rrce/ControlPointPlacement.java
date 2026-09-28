package dev.sjimo.rrce;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.sjimo.rrce.world.ParallelTrackAlignment;
import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class ControlPointPlacement {
    private ControlPointPlacement() {}

    static BlockPos position(Level level, BlockPos clicked, Direction face, ConstructionConfig config) {
        if (!(level.getBlockState(clicked).getBlock() instanceof TrackBlock)) return clicked.relative(face);
        if (config.lineCount != 2) return clicked;
        TrackShape shape = level.getBlockState(clicked).getValue(TrackBlock.SHAPE);
        if (shape.getAxes().size() != 1) return clicked;
        return ParallelTrackAlignment.center(clicked, shape.getAxes().get(0), config.spacing, candidate -> {
            var state = level.getBlockState(candidate);
            return state.getBlock() instanceof TrackBlock && state.getValue(TrackBlock.SHAPE) == shape;
        });
    }

    static TrackHeading heading(Level level, BlockPos clicked, Player player) {
        TrackHeading heading = TrackHeading.fromVector(player.getLookAngle());
        if (level.getBlockState(clicked).getBlock() instanceof TrackBlock) {
            var axes = level.getBlockState(clicked).getValue(TrackBlock.SHAPE).getAxes();
            if (axes.size() == 1) {
                Vec3 axis = axes.get(0);
                if (axis.dot(player.getLookAngle()) < 0) axis = axis.scale(-1);
                heading = TrackHeading.fromVector(axis);
            }
        }
        return heading;
    }
}
