package dev.sjimo.rrce.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Horizontal tunnel wall footprint, leaving the continuing railway open at each portal. */
final class TunnelGeometry {
    private TunnelGeometry() {}

    static Set<BlockPos> wallColumns(Set<BlockPos> interior, Set<BlockPos> roadbed, int thickness) {
        Set<BlockPos> shell = new HashSet<>(interior);
        Set<BlockPos> walls = new HashSet<>();
        Set<Long> routeColumns = new HashSet<>();
        for (BlockPos floor : roadbed) routeColumns.add(columnKey(floor));
        for (int layer = 0; layer < thickness; layer++) {
            Set<BlockPos> next = new HashSet<>();
            for (BlockPos floor : shell) {
                next.add(floor.north()); next.add(floor.south());
                next.add(floor.east()); next.add(floor.west());
            }
            next.removeAll(shell);
            for (BlockPos floor : next) if (!routeColumns.contains(columnKey(floor))) walls.add(floor);
            shell.addAll(next);
        }
        return walls;
    }

    /** Masks end-cap wall columns without changing terrain beyond the railway. */
    static Set<BlockPos> terminalWallOpenings(Set<BlockPos> corridor, Set<BlockPos> tunnelFloors,
                                              List<SurveyPoint> points, int depth) {
        Set<BlockPos> openings = new HashSet<>();
        if (tunnelFloors.isEmpty() || points.isEmpty()) return openings;
        for (int terminal = 0; terminal < 2; terminal++) {
            SurveyPoint point = points.get(terminal == 0 ? 0 : points.size() - 1);
            boolean reachesEnd = tunnelFloors.stream().anyMatch(floor ->
                Math.abs(floor.getX() - point.pos().getX()) <= depth + 1
                    && Math.abs(floor.getZ() - point.pos().getZ()) <= depth + 1
                    && Math.abs(floor.getY() - point.pos().getY() + 1) <= 2);
            if (!reachesEnd) continue;
            int direction = terminal == 0 ? -1 : 1;
            for (BlockPos floor : corridor) {
                double along = (floor.getX() - point.pos().getX()) * point.heading().vector().x
                    + (floor.getZ() - point.pos().getZ()) * point.heading().vector().z;
                if (Math.abs(along) > 1.5 || Math.abs(floor.getY() - point.pos().getY() + 1) > 2)
                    continue;
                for (int step = 1; step <= depth; step++)
                    openings.add(floor.offset(direction * point.heading().dx * step, 0,
                        direction * point.heading().dz * step));
            }
        }
        return openings;
    }

    static void reopenPassage(ConstructionPlan plan, Set<BlockPos> passage, Set<BlockPos> trackCells,
                              Set<BlockPos> foundationCells, int height) {
        for (BlockPos floor : passage) for (int h = 1; h <= height; h++) {
            BlockPos p = floor.above(h);
            if (trackCells.contains(p) || foundationCells.contains(p)) continue;
            BlockState planned = plan.blocks.get(p);
            if (planned != null && !planned.isAir()) plan.put(p, Blocks.AIR.defaultBlockState());
        }
    }

    private static long columnKey(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
    }
}
