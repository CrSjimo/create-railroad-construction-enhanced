package dev.sjimo.rrce.world;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Finds the center between a clicked rail and its parallel partner. */
public final class ParallelTrackAlignment {
    private ParallelTrackAlignment() {}

    public static BlockPos center(BlockPos clicked, Vec3 axis, int spacing,
                                  Predicate<BlockPos> compatibleTrack) {
        Vec3 tangent = new Vec3(axis.x, 0, axis.z).normalize();
        if (tangent.lengthSqr() < .5) return clicked;
        Vec3 normal = new Vec3(-tangent.z, 0, tangent.x);
        double separation = spacing + 1;
        // A preceding bend may have widened the pair to meet clearance after
        // rounding. Search the same widening range used by RouteGeometry.
        int radius = spacing + 34;
        BlockPos[] partners = new BlockPos[2];
        double[] scores = {Double.MAX_VALUE, Double.MAX_VALUE};
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx == 0 && dz == 0) continue;
            double along = dx * tangent.x + dz * tangent.z;
            double across = dx * normal.x + dz * normal.z;
            if (Math.abs(along) > .75 || Math.abs(across) < separation - .5
                || Math.abs(across) > separation + 32.5) continue;
            BlockPos candidate = clicked.offset(dx, 0, dz);
            if (!compatibleTrack.test(candidate)) continue;
            int side = across < 0 ? 0 : 1;
            double score = Math.abs(Math.abs(across) - separation) + Math.abs(along);
            if (score < scores[side]) { scores[side] = score; partners[side] = candidate; }
        }
        if ((partners[0] == null) == (partners[1] == null)) return clicked;
        BlockPos partner = partners[0] == null ? partners[1] : partners[0];
        return RouteGeometry.midpoint(clicked, partner);
    }
}
