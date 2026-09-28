package dev.sjimo.rrce.world;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Side slopes of a cutting, measured across the railway rather than along it. */
public final class CutGeometry {
    private CutGeometry() {}

    public static void forEachCell(Map<BlockPos, Integer> heights, Map<BlockPos, Vec3> tangents,
                                   double cutAngle, List<SurveyPoint> points, Consumer<BlockPos> cell) {
        double slope = 1.0 / Math.tan(Math.toRadians(cutAngle));
        SurveyPoint first = points.get(0), last = points.get(points.size() - 1);
        Vec3 firstCenter = Vec3.atCenterOf(first.pos()), lastCenter = Vec3.atCenterOf(last.pos());
        Vec3 firstAxis = first.heading().vector(), lastAxis = last.heading().vector();
        for (Map.Entry<BlockPos, Integer> entry : heights.entrySet()) {
            BlockPos floor = entry.getKey();
            Vec3 tangent = tangents.get(floor);
            if (tangent == null) continue;
            Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();
            if (normal.lengthSqr() < .5) continue;
            Vec3 source = Vec3.atCenterOf(floor);
            boolean startCap = isTerminalSection(source, tangent, firstCenter, firstAxis, true);
            boolean endCap = isTerminalSection(source, tangent, lastCenter, lastAxis, false);
            for (int h = 1; h <= Math.min(64, entry.getValue() + 2); h++) {
                int distance = (int) Math.ceil(h * slope);
                if (distance > 64) break;
                for (int quarter = -distance * 4; quarter <= distance * 4; quarter++) {
                    Vec3 lateral = source.add(normal.scale(quarter / 4.0));
                    BlockPos candidate = BlockPos.containing(lateral).atY(floor.getY() + h);
                    Vec3 candidateCenter = Vec3.atCenterOf(candidate);
                    if (startCap && candidateCenter.subtract(firstCenter).dot(firstAxis) < -1e-6) continue;
                    if (endCap && candidateCenter.subtract(lastCenter).dot(lastAxis) > 1e-6) continue;
                    cell.accept(candidate);
                }
            }
        }
    }

    private static boolean isTerminalSection(Vec3 source, Vec3 tangent, Vec3 endpoint,
                                             Vec3 axis, boolean start) {
        double along = source.subtract(endpoint).dot(axis);
        Vec3 horizontal = new Vec3(tangent.x, 0, tangent.z).normalize();
        return horizontal.dot(axis) > .9
            && (start ? along >= -1 && along <= 1.5 : along >= -1.5 && along <= 1);
    }
}
