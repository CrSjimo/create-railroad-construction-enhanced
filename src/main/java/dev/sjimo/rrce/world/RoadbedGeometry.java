package dev.sjimo.rrce.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.trains.track.BezierConnection;
import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/** Sweeps the railway cross section and follows Create's curved-track paving height. */
public final class RoadbedGeometry {
    public record Sample(Vec3 center, Vec3 tangent, int line) {}
    public record FoundationLayout(Set<BlockPos> floors, Map<BlockPos, BlockState> blocks,
                                   Map<BlockPos, Vec3> tangents) {}

    private RoadbedGeometry() {}

    public static void addCurveSamples(List<Sample> samples, BlockPos start, Vec3 startAxis,
                                       BezierConnection curve, BlockPos end, Vec3 endAxis, int line) {
        samples.add(new Sample(Vec3.atCenterOf(start), startAxis, line));
        for (BezierConnection.Segment segment : curve)
            samples.add(new Sample(segment.position.add(Vec3.atLowerCornerOf(start)),
                segment.derivative, line));
        samples.add(new Sample(Vec3.atCenterOf(end), endAxis, line));
    }

    public static Set<BlockPos> rasterize(List<Sample> samples, ConstructionConfig config) {
        return rasterize(samples, config, config.spacing + 1);
    }

    public static Set<BlockPos> rasterize(List<Sample> samples, ConstructionConfig config, int effectiveGap) {
        return foundationLayout(samples, config, effectiveGap).floors();
    }

    public static FoundationLayout foundationLayout(List<Sample> samples, ConstructionConfig config, int effectiveGap) {
        Map<Long, Double> lowestPaverHeight = new HashMap<>();
        Map<Long, Vec3> tangentByColumn = new HashMap<>();
        Sample[] previous = new Sample[config.lineCount];
        for (Sample sample : samples) {
            Sample before = previous[sample.line()];
            int longitudinalSteps = before == null ? 0 :
                Math.max(0, (int) Math.ceil(before.center().distanceTo(sample.center()) * 4));
            if (longitudinalSteps > 0 && longitudinalSteps < 64) {
                for (int step = 1; step < longitudinalSteps; step++) {
                    double t = step / (double) longitudinalSteps;
                    section(lowestPaverHeight, tangentByColumn, before.center().lerp(sample.center(), t),
                        before.tangent().lerp(sample.tangent(), t), sample.line(), config, effectiveGap);
                }
            }
            section(lowestPaverHeight, tangentByColumn, sample.center(), sample.tangent(),
                sample.line(), config, effectiveGap);
            previous[sample.line()] = sample;
        }

        Set<BlockPos> floors = new HashSet<>();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, Vec3> tangents = new HashMap<>();
        BlockState full = config.foundationState();
        boolean slab = full.getBlock() instanceof SlabBlock;
        for (Map.Entry<Long, Double> entry : lowestPaverHeight.entrySet()) {
            int x = (int) (entry.getKey() >> 32), z = (int) (long) entry.getKey();
            double yValue = entry.getValue();
            int y = (int) Math.floor(yValue);
            BlockPos base = new BlockPos(x, y, z);
            floors.add(base);
            tangents.put(base, tangentByColumn.get(entry.getKey()));
            // Create's TrackPaver places a lower slab above an upper slab when
            // the track passes through the upper half of a paving level.
            if (slab && yValue - y >= .5) {
                blocks.put(base, full.setValue(SlabBlock.TYPE, SlabType.TOP));
                blocks.put(base.above(), full.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            } else blocks.put(base, full);
        }
        return new FoundationLayout(floors, blocks, tangents);
    }

    private static void section(Map<Long, Double> heights, Map<Long, Vec3> tangents,
                                Vec3 center, Vec3 tangent, int line,
                                ConstructionConfig config, int effectiveGap) {
        Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();
        if (normal.lengthSqr() < .5) return;
        double halfGap = effectiveGap / 2.0;
        double low = line == 0 ? config.edgeMargin : halfGap;
        double high = line == config.lineCount - 1 ? config.edgeMargin : halfGap;
        // 1.125 below the sampled rail position is Create's TrackPaver height.
        double paverY = center.y - 1.125;
        for (int quarter = (int) Math.ceil(-low * 4); quarter <= (int) Math.floor(high * 4); quarter++) {
            Vec3 position = center.add(normal.scale(quarter / 4.0));
            long key = ((long) Math.floor(position.x) << 32) ^ ((long) Math.floor(position.z) & 0xffffffffL);
            Double prior = heights.get(key);
            if (prior == null || paverY <= prior) {
                heights.put(key, paverY);
                tangents.put(key, tangent);
            }
        }
    }
}
