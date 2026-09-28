package dev.sjimo.rrce.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.trains.track.BezierConnection;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class ConstructionPlan {
    public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    public final List<BezierConnection> curves = new ArrayList<>();

    public void put(BlockPos pos, BlockState state) {
        blocks.put(pos.immutable(), state);
        if (blocks.size() > 100_000) throw new UserFacingException("error.rrce.plan_too_large");
    }
}
