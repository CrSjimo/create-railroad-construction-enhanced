package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;

import com.simibubi.create.content.trains.track.BezierConnection;
import dev.sjimo.rrce.platform.CreateCouple;
import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RoadbedGeometryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        dev.sjimo.rrce.TestBootstrap.bootStrap();
    }

    @Test void risingCurvesUseCreateStyleHalfSlabsWithoutBuryingRail() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        config.edgeMargin = 0;
        assertState(config, 64.1875, 63, SlabType.DOUBLE, false);
        assertState(config, 64.8, 63, SlabType.TOP, true);
        assertState(config, 65.2, 64, SlabType.DOUBLE, false);
    }

    @Test void unsetFoundationKeepsExcavationFootprintButAirPlansClearing() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        List<RoadbedGeometry.Sample> samples = List.of(
            new RoadbedGeometry.Sample(new Vec3(.5, 64.8, .5), new Vec3(1, .1, 0), 0));
        var solid = RoadbedGeometry.foundationLayout(samples, config, 4);
        config.foundation = "";
        var unset = RoadbedGeometry.foundationLayout(samples, config, 4);
        assertTrue(unset.blocks().isEmpty());
        assertEquals(solid.floors(), unset.floors());
        assertEquals(solid.tangents(), unset.tangents());
        config.foundation = "minecraft:air";
        var air = RoadbedGeometry.foundationLayout(samples, config, 4);
        assertEquals(unset.floors(), air.blocks().keySet());
        assertTrue(air.blocks().values().stream().allMatch(state -> state.is(Blocks.AIR)));
    }

    @Test void blockMaterialValidationAcceptsOnlyUnsetAirAndSolidBlocks() {
        assertTrue(ConstructionConfig.isValidBlockMaterial(""));
        assertTrue(ConstructionConfig.isValidBlockMaterial("  "));
        assertTrue(ConstructionConfig.isValidBlockMaterial("minecraft:air"));
        assertTrue(ConstructionConfig.isValidBlockMaterial("minecraft:cave_air"));
        assertTrue(ConstructionConfig.isValidBlockMaterial("minecraft:stone_brick_slab"));
        assertFalse(ConstructionConfig.isValidBlockMaterial("minecraft:water"));
        assertFalse(ConstructionConfig.isValidBlockMaterial("minecraft:does_not_exist"));
        assertFalse(ConstructionConfig.isValidBlockMaterial("invalid id"));
    }

    @Test void curvedRailEndsSweepTheWholeCrossSection() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        config.edgeMargin = 2;
        BlockPos start = new BlockPos(0, 64, 0), end = new BlockPos(40, 64, 40);
        Vec3 east = new Vec3(1, 0, 0), south = new Vec3(0, 0, 1);
        BezierConnection curve = new BezierConnection(CreateCouple.create(start, end),
            CreateCouple.create(RouteGeometry.curveStart(start, east), RouteGeometry.curveStart(end, south.scale(-1))),
            CreateCouple.create(east, south.scale(-1)),
            CreateCouple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
        List<RoadbedGeometry.Sample> samples = new ArrayList<>();
        RoadbedGeometry.addCurveSamples(samples, start, east, curve, end, south, 0);
        assertEquals(Vec3.atCenterOf(start), samples.get(0).center());
        assertEquals(Vec3.atCenterOf(end), samples.get(samples.size() - 1).center());
        var pavement = RoadbedGeometry.foundationLayout(samples, config, 4);
        for (int z = -2; z <= 2; z++)
            assertTrue(pavement.floors().contains(new BlockPos(0, 63, z)), "start cap z=" + z);
        for (int x = 38; x <= 42; x++)
            assertTrue(pavement.floors().contains(new BlockPos(x, 63, 40)), "end cap x=" + x);
    }

    private static void assertState(ConstructionConfig config, double railY, int baseY,
                                    SlabType expectedBase, boolean raised) {
        var layout = RoadbedGeometry.foundationLayout(List.of(
            new RoadbedGeometry.Sample(new Vec3(.5, railY, .5), new Vec3(1, .1, 0), 0)), config, 4);
        BlockPos base = new BlockPos(0, baseY, 0);
        assertTrue(layout.floors().contains(base));
        assertEquals(expectedBase, layout.blocks().get(base).getValue(SlabBlock.TYPE));
        if (raised) {
            assertEquals(SlabType.BOTTOM, layout.blocks().get(base.above()).getValue(SlabBlock.TYPE));
            assertTrue(baseY + 1.5 <= railY - .125 + 1e-8);
        } else assertFalse(layout.blocks().containsKey(base.above()));
    }

    @Test void actualCreateSlopeAndTurningSlopeClearTheirFoundation() {
        ConstructionConfig config = new ConstructionConfig();
        for (SurveyPoint destination : List.of(
            new SurveyPoint(new BlockPos(40, 68, 0), TrackHeading.EAST),
            new SurveyPoint(new BlockPos(50, 68, 30), TrackHeading.SOUTH_EAST),
            new SurveyPoint(new BlockPos(40, 68, 40), TrackHeading.SOUTH))) {
            var layout = RouteGeometry.buildLayout(List.of(
                new SurveyPoint(new BlockPos(0, 64, 0), TrackHeading.EAST), destination), config);
            List<RoadbedGeometry.Sample> samples = new ArrayList<>();
            for (RouteGeometry.Stroke stroke : layout.strokes()) {
                if (!stroke.curve()) {
                    Vec3 a = Vec3.atCenterOf(stroke.start()), b = Vec3.atCenterOf(stroke.end());
                    int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) * 2));
                    for (int step = 0; step <= steps; step++)
                        samples.add(new RoadbedGeometry.Sample(a.lerp(b, step / (double) steps),
                            stroke.startAxis(), stroke.line()));
                } else {
                    BezierConnection curve = new BezierConnection(
                        CreateCouple.create(stroke.start(), stroke.end()),
                        CreateCouple.create(RouteGeometry.curveStart(stroke.start(), stroke.startAxis()),
                            RouteGeometry.curveStart(stroke.end(), stroke.endAxis().scale(-1))),
                        CreateCouple.create(stroke.startAxis(), stroke.endAxis().scale(-1)),
                        CreateCouple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
                    for (BezierConnection.Segment segment : curve)
                        samples.add(new RoadbedGeometry.Sample(
                            segment.position.add(Vec3.atLowerCornerOf(stroke.start())), segment.derivative,
                            stroke.line()));
                }
            }
            var pavement = RoadbedGeometry.foundationLayout(samples, config, layout.effectiveGap());
            assertTrue(pavement.blocks().values().stream()
                .anyMatch(state -> state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM));
            for (RoadbedGeometry.Sample sample : samples) {
                BlockPos column = BlockPos.containing(sample.center());
                double highestSurface = Double.NEGATIVE_INFINITY;
                for (var entry : pavement.blocks().entrySet()) {
                    BlockPos p = entry.getKey();
                    if (p.getX() != column.getX() || p.getZ() != column.getZ()) continue;
                    double top = p.getY() + (entry.getValue().getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? .5 : 1);
                    highestSurface = Math.max(highestSurface, top);
                }
                assertTrue(highestSurface <= sample.center().y - .125 + .001,
                    "foundation buries rail near " + column + " on " + destination);
            }
        }
    }
}
