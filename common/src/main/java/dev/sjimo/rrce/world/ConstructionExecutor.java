package dev.sjimo.rrce.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Runs on the server thread after the planner has validated the complete task. */
public final class ConstructionExecutor {
    private ConstructionExecutor() {}

    public static EditHistory execute(ServerLevel level, ConstructionPlan plan) {
        EditHistory history = new EditHistory(level);
        for (BlockPos p : plan.blocks.keySet()) {
            if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p))
                throw new UserFacingException("error.rrce.position_unavailable", p.toShortString());
            history.captureBefore(level, p);
        }
        for (BezierConnection curve : plan.curves) {
            history.captureBefore(level, dev.sjimo.rrce.platform.CreateApi.first(curve));
            history.captureBefore(level, dev.sjimo.rrce.platform.CreateApi.second(curve));
            // Create creates one fake collision track around every other curve sample.
            for (BezierConnection.Segment segment : curve) {
                BlockPos p = BlockPos.containing(segment.position.add(net.minecraft.world.phys.Vec3.atLowerCornerOf(dev.sjimo.rrce.platform.CreateApi.first(curve))));
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    for (int y = -1; y <= 2; y++) {
                        BlockPos around = p.offset(x, y, z);
                        if (level.isOutsideBuildHeight(around)) continue;
                        if (!level.hasChunkAt(around))
                            throw new UserFacingException("error.rrce.chunk_unloaded", around.toShortString());
                        history.captureBefore(level, around);
                    }
            }
        }
        if (history.size() > 100_000) throw new UserFacingException("error.rrce.history_too_large");
        try {
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(plan.blocks.entrySet());
            entries.sort(Comparator.comparingInt(e -> e.getValue().isAir() ? 0 :
                e.getValue().getBlock() instanceof com.simibubi.create.content.trains.track.TrackBlock ? 2 : 1));
            for (Map.Entry<BlockPos, BlockState> entry : entries) {
                BlockPos p = entry.getKey();
                BlockState state = entry.getValue();
                if (level.getBlockState(p).equals(state)) continue;
                // A matching Create track may already carry connections to earlier
                // construction. Keep its block entity when only HAS_BE changes.
                if (!(state.getBlock() instanceof com.simibubi.create.content.trains.track.TrackBlock)
                    && level.getBlockEntity(p) != null) level.removeBlockEntity(p);
                if (!level.setBlock(p, state, 3))
                    throw new UserFacingException("error.rrce.cannot_place_block", p.toShortString());
            }
            for (BezierConnection curve : plan.curves) {
                BlockPos a = dev.sjimo.rrce.platform.CreateApi.first(curve), b = dev.sjimo.rrce.platform.CreateApi.second(curve);
                if (!(level.getBlockEntity(a) instanceof TrackBlockEntity first)
                    || !(level.getBlockEntity(b) instanceof TrackBlockEntity second))
                    throw new UserFacingException("error.rrce.curve_end_missing", a.toShortString(), b.toShortString());
                first.addConnection(curve);
                second.addConnection(curve.secondary());
            }
            history.captureAfter(level);
            return history;
        } catch (RuntimeException failure) {
            history.undo(level);
            throw failure;
        }
    }
}
