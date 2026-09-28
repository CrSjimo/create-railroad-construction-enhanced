package dev.sjimo.rrce.world;

import java.util.ArrayList;
import java.util.List;

import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Connects oriented Create-style rail selections using maximum-radius turns. */
public final class RouteGeometry {
    public record Stroke(BlockPos start, BlockPos end, Vec3 startAxis, Vec3 endAxis, boolean curve, int line) {}
    public record Layout(List<Stroke> strokes, int effectiveGap) {}

    private RouteGeometry() {}

    public static List<Stroke> build(List<SurveyPoint> points, ConstructionConfig config) {
        return buildLayout(points, config).strokes();
    }

    public static Layout buildLayout(List<SurveyPoint> points, ConstructionConfig config) {
        return buildLayout(points, config, false);
    }

    public static Layout buildLayout(List<SurveyPoint> points, ConstructionConfig config,
                                     boolean relaxedPlacementChecks) {
        if (points.size() < 2) throw new UserFacingException("error.rrce.need_two_points");
        if (points.size() > 64) throw new UserFacingException("error.rrce.too_many_points");
        double estimatedRailLength = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            estimatedRailLength += Math.sqrt(points.get(i).pos().distSqr(points.get(i + 1).pos()));
            appendConnection(new ArrayList<>(), points.get(i).pos(), points.get(i + 1).pos(),
                points.get(i).heading(), points.get(i + 1).heading(), 0, 0, 0, relaxedPlacementChecks);
        }
        if (estimatedRailLength * config.lineCount > 100_000)
            throw new UserFacingException("error.rrce.route_too_long");
        int requestedGap = config.spacing + 1;
        int count = points.size();
        double[][] best = new double[count][2];
        int[][] previous = new int[count][2];
        @SuppressWarnings("unchecked")
        List<Stroke>[][] chosenEdges = new List[count][2];
        for (int i = 0; i < count; i++) for (int option = 0; option < 2; option++) {
            best[i][option] = Double.POSITIVE_INFINITY;
            previous[i][option] = -1;
        }
        best[0][0] = 0;
        if (offsetOptions(points.get(0), config.lineCount, requestedGap) == 2) best[0][1] = 0;
        for (int edge = 0; edge < count - 1; edge++) {
            for (int a = 0; a < offsetOptions(points.get(edge), config.lineCount, requestedGap); a++) {
                if (!Double.isFinite(best[edge][a])) continue;
                for (int b = 0; b < offsetOptions(points.get(edge + 1), config.lineCount, requestedGap); b++) {
                    List<Stroke> candidate;
                    try {
                        candidate = buildEdge(points.get(edge), points.get(edge + 1), config,
                            requestedGap, a, b, relaxedPlacementChecks);
                    } catch (IllegalArgumentException invalid) {
                        continue;
                    }
                    if (!RailClearance.hasRequiredSpacing(candidate, config.lineCount, requestedGap)) continue;
                    double spread = RailClearance.maximumLocalSpacing(candidate, config.lineCount,
                        requestedGap);
                    if (spread > maximumPermittedGap(requestedGap)) continue;
                    double score = Math.max(best[edge][a], spread);
                    if (score < best[edge + 1][b]) {
                        best[edge + 1][b] = score;
                        previous[edge + 1][b] = a;
                        chosenEdges[edge + 1][b] = candidate;
                    }
                }
            }
        }
        int last = best[count - 1][0] <= best[count - 1][1] ? 0 : 1;
        if (!Double.isFinite(best[count - 1][last]))
            throw new UserFacingException("error.rrce.curve_spacing");
        List<Stroke> strokes = new ArrayList<>();
        List<List<Stroke>> edges = new ArrayList<>();
        for (int i = count - 1; i > 0; i--) {
            edges.add(chosenEdges[i][last]);
            last = previous[i][last];
        }
        for (int i = edges.size() - 1; i >= 0; i--) strokes.addAll(edges.get(i));
        if (!RailClearance.hasRequiredSpacing(strokes, config.lineCount, requestedGap))
            throw new UserFacingException("error.rrce.curve_clearance_conflict");
        return new Layout(strokes, requestedGap);
    }

    static List<Stroke> buildWithGap(List<SurveyPoint> points, ConstructionConfig config, int effectiveGap) {
        List<Stroke> result = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++)
            result.addAll(buildEdge(points.get(i), points.get(i + 1), config, effectiveGap, 0, 0));
        return result;
    }

    static List<Stroke> buildEdge(SurveyPoint first, SurveyPoint second,
                                          ConstructionConfig config, int gap, int firstOption, int secondOption) {
        return buildEdge(first, second, config, gap, firstOption, secondOption, false);
    }

    private static List<Stroke> buildEdge(SurveyPoint first, SurveyPoint second,
                                          ConstructionConfig config, int gap, int firstOption, int secondOption,
                                          boolean relaxedPlacementChecks) {
        List<Stroke> result = new ArrayList<>();
        for (int line = 0; line < config.lineCount; line++) {
            BlockPos a = shift(first, line, config.lineCount, gap, firstOption);
            BlockPos b = shift(second, line, config.lineCount, gap, secondOption);
            appendConnection(result, a, b, first.heading(), second.heading(), line, 0, 0,
                relaxedPlacementChecks);
        }
        if (RailClearance.hasRequiredSpacing(result, config.lineCount, gap)
            && RailClearance.maximumLocalSpacing(result, config.lineCount, gap) <= maximumPermittedGap(gap))
            return result;
        return optimizeCurveJunctions(first, second, config, gap, firstOption, secondOption,
            relaxedPlacementChecks);
    }

    private record Variant(List<Stroke> strokes, int changes) {}

    private static List<Stroke> optimizeCurveJunctions(SurveyPoint first, SurveyPoint second,
                                                        ConstructionConfig config, int gap,
                                                        int firstOption, int secondOption,
                                                        boolean relaxedPlacementChecks) {
        List<List<Variant>> variants = new ArrayList<>();
        int[][] adjustments = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-2, 0}, {2, 0}, {0, -2}, {0, 2}, {1, 1}, {2, 2}};
        for (int line = 0; line < config.lineCount; line++) {
            BlockPos a = shift(first, line, config.lineCount, gap, firstOption);
            BlockPos b = shift(second, line, config.lineCount, gap, secondOption);
            List<Variant> choices = new ArrayList<>();
            for (int[] adjustment : adjustments) {
                try {
                    List<Stroke> candidate = new ArrayList<>();
                    appendConnection(candidate, a, b, first.heading(), second.heading(), line,
                        adjustment[0], adjustment[1], relaxedPlacementChecks);
                    if (choices.stream().noneMatch(existing -> existing.strokes().equals(candidate)))
                        choices.add(new Variant(candidate, Math.abs(adjustment[0]) + Math.abs(adjustment[1])));
                } catch (IllegalArgumentException ignored) {
                    // An adjustment can leave too little space for a curve.
                }
            }
            variants.add(choices);
        }
        List<double[]> scores = new ArrayList<>();
        List<int[]> predecessor = new ArrayList<>();
        for (int line = 0; line < config.lineCount; line++) {
            int size = variants.get(line).size();
            double[] row = new double[size];
            int[] prev = new int[size];
            java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(prev, -1);
            if (line == 0) for (int v = 0; v < size; v++) row[v] = variants.get(0).get(v).changes() * .001;
            else for (int v = 0; v < size; v++) for (int p = 0; p < variants.get(line - 1).size(); p++) {
                if (!Double.isFinite(scores.get(line - 1)[p])) continue;
                List<Stroke> pair = new ArrayList<>();
                for (Stroke stroke : variants.get(line - 1).get(p).strokes())
                    pair.add(new Stroke(stroke.start(), stroke.end(), stroke.startAxis(), stroke.endAxis(),
                        stroke.curve(), 0));
                for (Stroke stroke : variants.get(line).get(v).strokes())
                    pair.add(new Stroke(stroke.start(), stroke.end(), stroke.startAxis(), stroke.endAxis(),
                        stroke.curve(), 1));
                if (!RailClearance.hasRequiredSpacing(pair, 2, gap)) continue;
                double maximum = RailClearance.maximumLocalSpacing(pair, 2, gap);
                if (maximum > maximumPermittedGap(gap)) continue;
                double score = Math.max(scores.get(line - 1)[p], maximum)
                    + variants.get(line).get(v).changes() * .001;
                if (score < row[v]) { row[v] = score; prev[v] = p; }
            }
            scores.add(row);
            predecessor.add(prev);
        }
        double[] finalScores = scores.get(config.lineCount - 1);
        int best = -1;
        for (int v = 0; v < finalScores.length; v++)
            if (best < 0 || finalScores[v] < finalScores[best]) best = v;
        if (best < 0 || !Double.isFinite(finalScores[best]))
            throw new UserFacingException("error.rrce.curve_spacing");
        List<List<Stroke>> selected = new ArrayList<>();
        for (int line = config.lineCount - 1; line >= 0; line--) {
            selected.add(variants.get(line).get(best).strokes());
            best = predecessor.get(line)[best];
        }
        List<Stroke> result = new ArrayList<>();
        for (int line = selected.size() - 1; line >= 0; line--) result.addAll(selected.get(line));
        if (!RailClearance.hasRequiredSpacing(result, config.lineCount, gap))
            throw new UserFacingException("error.rrce.curve_spacing");
        return result;
    }

    private static double maximumPermittedGap(int gap) {
        // A diagonal rail can move only in whole diagonal lattice steps.
        // This is the smallest attainable center spacing at 45 degrees.
        return Math.max(gap, Math.ceil(gap / Math.sqrt(2) - 1e-8) * Math.sqrt(2)) + .15;
    }

    private static int offsetOptions(SurveyPoint point, int count, int gap) {
        int step = point.heading().dx != 0 && point.heading().dz != 0
            ? (int) Math.ceil(gap / Math.sqrt(2) - 1e-8) : gap;
        return (count - 1) * step % 2 == 0 ? 1 : 2;
    }

    private static BlockPos shift(SurveyPoint point, int line, int count, int gap, int option) {
        TrackHeading heading = point.heading();
        int step = heading.dx != 0 && heading.dz != 0
            ? (int) Math.ceil(gap / Math.sqrt(2) - 1e-8) : gap;
        int span = (count - 1) * step;
        int offset = line * step - (option == 0 ? Math.floorDiv(span, 2) : (span + 1) / 2);
        return point.pos().offset(-heading.dz * offset, 0, heading.dx * offset);
    }

    private static void appendConnection(List<Stroke> out, BlockPos a, BlockPos b,
                                         TrackHeading first, TrackHeading second, int line) {
        appendConnection(out, a, b, first, second, line, 0, 0, false);
    }

    private static void appendConnection(List<Stroke> out, BlockPos a, BlockPos b,
                                         TrackHeading first, TrackHeading second, int line,
                                         int adjustA, int adjustB, boolean relaxedPlacementChecks) {
        if (a.equals(b)) throw new UserFacingException("error.rrce.adjacent_points_overlap");
        if (a.distSqr(b) > 512 * 512) throw new UserFacingException("error.rrce.segment_too_long");
        Vec3 forward = first.vector(), arrival = second.vector();
        Vec3 delta = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
        double horizontal = delta.length();
        if (horizontal < 2) throw new UserFacingException("error.rrce.points_too_close");
        double turn = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward.dot(arrival)))));
        if (turn > 90.01) throw new UserFacingException("error.rrce.turn_over_ninety");
        boolean aligned = turn < .1 && Math.abs(cross(forward, delta)) < 1e-6
            && forward.dot(delta) > 0;
        int rise = Math.abs(b.getY() - a.getY());
        if (rise > 0) {
            if (aligned) {
                double required = Math.max(6, rise < 4 ? rise * 4 : rise * 3);
                if (!relaxedPlacementChecks && horizontal + 1e-6 < required)
                    throw new UserFacingException("error.rrce.slope_too_steep");
                out.add(new Stroke(a, b, forward, arrival, true, line));
                return;
            }
        }
        if (aligned) {
            out.add(new Stroke(a, b, forward, arrival, false, line));
            return;
        }
        if (turn < .1) {
            if (rise > 0 && !relaxedPlacementChecks)
                throw new UserFacingException("error.rrce.sloped_s_curve");
            double along = forward.dot(delta);
            double across = Math.abs(cross(forward, delta));
            if (!relaxedPlacementChecks && along < Math.max(6, across * 2))
                throw new UserFacingException("error.rrce.s_curve_too_sharp");
            int startExtent = Math.max(0, adjustA), endExtent = Math.max(0, adjustB);
            BlockPos curveA = a.offset(first.dx * startExtent, 0, first.dz * startExtent);
            BlockPos curveB = b.offset(-second.dx * endExtent, 0, -second.dz * endExtent);
            if (curveA.distSqr(curveB) < 36) throw new UserFacingException("error.rrce.s_curve_too_short");
            if (!curveA.equals(a)) out.add(new Stroke(a, curveA, forward, forward, false, line));
            out.add(new Stroke(curveA, curveB, forward, arrival, true, line));
            if (!curveB.equals(b)) out.add(new Stroke(curveB, b, arrival, arrival, false, line));
            return;
        }

        // Create's maximiseTurn=true branch only extends the longer side
        // until the available tangent lengths match.
        Vec3 endA = curveStart(a, forward);
        Vec3 backward = arrival.scale(-1);
        Vec3 endB = curveStart(b, backward);
        double denominator = cross(forward, backward);
        if (Math.abs(denominator) < 1e-8) throw new UserFacingException("error.rrce.turn_impossible");
        Vec3 between = endB.subtract(endA);
        double distanceA = cross(between, backward) / denominator;
        double distanceB = cross(between, forward) / denominator;
        double minRadius = turn > 60 ? 7 + Math.max(0, rise - 3) * 2
            : 3.25 + Math.max(0, rise - 1.5) * 1.5;
        if ((!relaxedPlacementChecks && (distanceA < minRadius + .1 || distanceB < minRadius + .1))
            || relaxedPlacementChecks && (distanceA <= .1 || distanceB <= .1))
            throw new UserFacingException(rise > 0
                ? "error.rrce.climbing_curve_too_steep" : "error.rrce.curve_radius_small");
        int extentA = Math.max(0, (int) Math.ceil(Math.max(0, distanceA - distanceB) / first.rawAxis().length()) + adjustA);
        int extentB = Math.max(0, (int) Math.ceil(Math.max(0, distanceB - distanceA) / second.rawAxis().length()) + adjustB);
        BlockPos curveA = a.offset(first.dx * extentA, 0, first.dz * extentA);
        BlockPos curveB = b.offset(-second.dx * extentB, 0, -second.dz * extentB);
        if (a.distSqr(curveA) > 512 * 512 || b.distSqr(curveB) > 512 * 512)
            throw new UserFacingException("error.rrce.points_too_far");
        if (!curveA.equals(a)) out.add(new Stroke(a, curveA, forward, forward, false, line));
        out.add(new Stroke(curveA, curveB, forward, arrival, true, line));
        if (!curveB.equals(b)) out.add(new Stroke(curveB, b, arrival, arrival, false, line));
    }

    static Vec3 curveStart(BlockPos pos, Vec3 outward) {
        // TrackBlock.getCurveStart uses the unnormalized TrackShape axis. This
        // matters on diagonal track: its half-step is (±.5, ±.5), not .3535.
        Vec3 raw = new Vec3(Math.signum(outward.x), 0, Math.signum(outward.z));
        return Vec3.atCenterOf(pos).add(0, -.5, 0).add(raw.scale(.5));
    }

    public static BlockPos midpoint(BlockPos first, BlockPos second) {
        return new BlockPos(Math.floorDiv(first.getX() + second.getX(), 2), first.getY(),
            Math.floorDiv(first.getZ() + second.getZ(), 2));
    }

    private static double cross(Vec3 a, Vec3 b) { return a.x * b.z - a.z * b.x; }
}
