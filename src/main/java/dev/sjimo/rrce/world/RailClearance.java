package dev.sjimo.rrce.world;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.utility.Couple;
import net.minecraft.world.phys.Vec3;

/** Checks horizontal centerline clearance between complete neighboring routes. */
final class RailClearance {
    private record Segment(double ax, double az, double bx, double bz) {}

    private RailClearance() {}

    static boolean hasRequiredSpacing(List<RouteGeometry.Stroke> strokes, int lines, int separation) {
        if (lines < 2) return true;
        List<List<Segment>> byLine = new ArrayList<>();
        for (int i = 0; i < lines; i++) byLine.add(new ArrayList<>());
        double[] approximationError = new double[lines];
        for (RouteGeometry.Stroke stroke : strokes) {
            approximationError[stroke.line()] = Math.max(approximationError[stroke.line()],
                appendSegments(byLine.get(stroke.line()), stroke));
        }
        for (int i = 0; i < lines - 1; i++) for (int j = i + 1; j < lines; j++)
            if (!separate(byLine.get(i), byLine.get(j),
                separation + approximationError[i] + approximationError[j]
                    + (approximationError[i] + approximationError[j] > 0 ? 1e-6 : 0))) return false;
        return true;
    }

    /** Largest nearest-neighbour centerline distance along one survey edge. */
    static double maximumLocalSpacing(List<RouteGeometry.Stroke> strokes, int lines, int expected) {
        if (lines < 2) return 0;
        List<List<Segment>> byLine = new ArrayList<>();
        for (int i = 0; i < lines; i++) byLine.add(new ArrayList<>());
        for (RouteGeometry.Stroke stroke : strokes) appendSegments(byLine.get(stroke.line()), stroke);
        double largest = 0;
        for (int i = 0; i < lines - 1; i++) {
            largest = Math.max(largest, directedMaximum(byLine.get(i), byLine.get(i + 1), expected));
            largest = Math.max(largest, directedMaximum(byLine.get(i + 1), byLine.get(i), expected));
        }
        return largest;
    }

    private static double directedMaximum(List<Segment> source, List<Segment> target, int expected) {
        int size = Math.max(4, expected + 4);
        Map<Long, List<Segment>> buckets = new HashMap<>();
        for (Segment s : target) {
            int x0 = cell(Math.min(s.ax, s.bx), size), x1 = cell(Math.max(s.ax, s.bx), size);
            int z0 = cell(Math.min(s.az, s.bz), size), z1 = cell(Math.max(s.az, s.bz), size);
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                buckets.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(s);
        }
        double maximum = 0;
        for (Segment s : source) for (int endpoint = 0; endpoint < 2; endpoint++) {
            double px = endpoint == 0 ? s.ax : s.bx;
            double pz = endpoint == 0 ? s.az : s.bz;
            int cx = cell(px, size), cz = cell(pz, size);
            double nearest = Double.POSITIVE_INFINITY;
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
                List<Segment> candidates = buckets.get(key(x, z));
                if (candidates == null) continue;
                for (Segment candidate : candidates)
                    nearest = Math.min(nearest, pointSegmentSquared(px, pz, candidate));
            }
            maximum = Math.max(maximum, Math.sqrt(nearest));
        }
        return maximum;
    }

    private static double appendSegments(List<Segment> into, RouteGeometry.Stroke stroke) {
        if (!stroke.curve()) {
            into.add(new Segment(stroke.start().getX() + .5, stroke.start().getZ() + .5,
                stroke.end().getX() + .5, stroke.end().getZ() + .5));
            return 0;
        }
        Vec3 first = stroke.startAxis(), last = stroke.endAxis();
        BezierConnection curve = new BezierConnection(Couple.create(stroke.start(), stroke.end()),
            Couple.create(RouteGeometry.curveStart(stroke.start(), first),
                RouteGeometry.curveStart(stroke.end(), last.scale(-1))),
            Couple.create(first, last.scale(-1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
        int steps = Math.max(2, (int) Math.ceil(curve.getLength() * 8));
        Vec3 previous = curve.getPosition(0);
        into.add(new Segment(stroke.start().getX() + .5, stroke.start().getZ() + .5,
            previous.x, previous.z));
        for (int step = 1; step <= steps; step++) {
            Vec3 current = curve.getPosition(step / (double) steps);
            into.add(new Segment(previous.x, previous.z, current.x, current.z));
            previous = current;
        }
        into.add(new Segment(previous.x, previous.z,
            stroke.end().getX() + .5, stroke.end().getZ() + .5));
        // A cubic Bézier's second derivative is linear in t. The maximum norm
        // is bounded by the two endpoint values; linear chord error on a
        // uniform interval h is at most max|B''| * h² / 8.
        double handle = curve.getHandleLength();
        Vec3 p0 = RouteGeometry.curveStart(stroke.start(), first);
        Vec3 p3 = RouteGeometry.curveStart(stroke.end(), last.scale(-1));
        Vec3 p1 = p0.add(first.scale(handle));
        Vec3 p2 = p3.add(last.scale(-handle));
        Vec3 secondAtStart = p0.subtract(p1.scale(2)).add(p2);
        Vec3 secondAtEnd = p1.subtract(p2.scale(2)).add(p3);
        double curvature = 6 * Math.max(horizontalLength(secondAtStart), horizontalLength(secondAtEnd));
        return curvature / (8.0 * steps * steps);
    }

    private static double horizontalLength(Vec3 vector) {
        return Math.hypot(vector.x, vector.z);
    }

    private static boolean separate(List<Segment> first, List<Segment> second, double required) {
        int cellSize = Math.max(4, (int) Math.ceil(required));
        Map<Long, BitSet> buckets = new HashMap<>();
        for (int i = 0; i < first.size(); i++) {
            Segment s = first.get(i);
            int minX = cell(Math.min(s.ax, s.bx) - required, cellSize);
            int maxX = cell(Math.max(s.ax, s.bx) + required, cellSize);
            int minZ = cell(Math.min(s.az, s.bz) - required, cellSize);
            int maxZ = cell(Math.max(s.az, s.bz) + required, cellSize);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                buckets.computeIfAbsent(key(x, z), ignored -> new BitSet()).set(i);
        }
        // Create's cubic interpolation and the sampled chord model differ at
        // sub-milliblock scale; leave numerical tolerance below one thousandth.
        double minimumSquared = (required - .001) * (required - .001);
        for (Segment other : second) {
            int minX = cell(Math.min(other.ax, other.bx), cellSize);
            int maxX = cell(Math.max(other.ax, other.bx), cellSize);
            int minZ = cell(Math.min(other.az, other.bz), cellSize);
            int maxZ = cell(Math.max(other.az, other.bz), cellSize);
            BitSet candidates = new BitSet();
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                BitSet matches = buckets.get(key(x, z));
                if (matches != null) candidates.or(matches);
            }
            for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1))
                if (distanceSquared(first.get(index), other) < minimumSquared) return false;
        }
        return true;
    }

    private static int cell(double coordinate, int size) { return (int) Math.floor(coordinate / size); }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static double cross(double ax, double az, double bx, double bz) { return ax * bz - az * bx; }

    private static double distanceSquared(Segment a, Segment b) {
        double arx = a.bx - a.ax, arz = a.bz - a.az;
        double brx = b.bx - b.ax, brz = b.bz - b.az;
        double den = cross(arx, arz, brx, brz);
        if (Math.abs(den) > 1e-10) {
            double qx = b.ax - a.ax, qz = b.az - a.az;
            double t = cross(qx, qz, brx, brz) / den;
            double u = cross(qx, qz, arx, arz) / den;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) return 0;
        }
        return Math.min(Math.min(pointSegmentSquared(a.ax, a.az, b), pointSegmentSquared(a.bx, a.bz, b)),
            Math.min(pointSegmentSquared(b.ax, b.az, a), pointSegmentSquared(b.bx, b.bz, a)));
    }

    private static double pointSegmentSquared(double x, double z, Segment s) {
        double dx = s.bx - s.ax, dz = s.bz - s.az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared < 1e-12 ? 0 :
            Math.max(0, Math.min(1, ((x - s.ax) * dx + (z - s.az) * dz) / lengthSquared));
        double rx = x - (s.ax + dx * t), rz = z - (s.az + dz * t);
        return rx * rx + rz * rz;
    }
}
