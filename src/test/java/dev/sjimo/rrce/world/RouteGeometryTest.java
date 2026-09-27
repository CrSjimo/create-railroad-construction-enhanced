package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.utility.Couple;
import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RouteGeometryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SurveyPoint point(int x, int y, int z, TrackHeading direction) {
        return new SurveyPoint(new BlockPos(x, y, z), direction);
    }

    @Test void defaultFoundationIsFullHeightAndStraightRouteHasFourBlockCenterSpacing() {
        ConstructionConfig config = new ConstructionConfig();
        assertEquals(2, config.lineCount);
        assertEquals(3, config.spacing);
        assertEquals(2, config.edgeMargin);
        assertEquals(SlabType.DOUBLE, config.foundationState().getValue(SlabBlock.TYPE));
        assertTrue(config.foundationState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        List<RouteGeometry.Stroke> strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 64, 0, TrackHeading.EAST)), config);
        assertEquals(2, strokes.size());
        assertTrue(strokes.stream().noneMatch(RouteGeometry.Stroke::curve));
        assertEquals(4, strokes.get(1).start().getZ() - strokes.get(0).start().getZ());
    }

    @Test void curvesUseSmallestPracticalGapWithoutWideningStraightEndpoints() {
        ConstructionConfig config = new ConstructionConfig();
        for (SurveyPoint end : List.of(point(40, 64, 40, TrackHeading.SOUTH),
            point(50, 64, 30, TrackHeading.SOUTH_EAST), point(50, 64, 6, TrackHeading.EAST))) {
            var strokes = RouteGeometry.build(List.of(point(0, 64, 0, TrackHeading.EAST), end), config);
            assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
            assertTrue(RailClearance.maximumLocalSpacing(strokes, 2, 4) < 4.27, end.toString());
            var starts = strokes.stream().filter(s -> s.start().getX() == 0).toList();
            assertEquals(2, starts.size());
            assertEquals(4, Math.abs(starts.get(0).start().getZ() - starts.get(1).start().getZ()));
        }
        var diagonal = RouteGeometry.build(List.of(point(0, 64, 0, TrackHeading.SOUTH_EAST),
            point(40, 64, 40, TrackHeading.SOUTH_EAST)), config);
        assertEquals(3, Math.abs(diagonal.get(0).start().getX() - diagonal.get(1).start().getX()));
        assertEquals(3, Math.abs(diagonal.get(0).start().getZ() - diagonal.get(1).start().getZ()));
    }

    @Test void twoOrientedEndsMakeAFullRadiusNinetyDegreeTurn() {
        ConstructionConfig config = new ConstructionConfig();
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 64, 40, TrackHeading.SOUTH)), config);
        assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
        for (RouteGeometry.Stroke stroke : strokes.stream().filter(RouteGeometry.Stroke::curve).toList()) {
            assertEquals(0, stroke.startAxis().dot(stroke.endAxis()), 1e-5);
            assertTrue(curve(stroke).getRadius() >= 7);
        }
    }

    @Test void untrackedLineStillParticipatesInCurvesAndClearance() {
        ConstructionConfig config = new ConstructionConfig();
        config.materials.set(0, "");
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 64, 40, TrackHeading.SOUTH)), config);
        assertEquals(2, strokes.size());
        assertTrue(strokes.stream().allMatch(RouteGeometry.Stroke::curve));
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
    }

    @Test void twoOrientedEndsMakeAFortyFiveDegreeTurn() {
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(50, 64, 30, TrackHeading.SOUTH_EAST)),
            new ConstructionConfig());
        assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
        assertEquals(Math.sqrt(.5), strokes.stream().filter(RouteGeometry.Stroke::curve)
            .findFirst().orElseThrow().startAxis().dot(
                strokes.stream().filter(RouteGeometry.Stroke::curve).findFirst().orElseThrow().endAxis()), 1e-5);
    }

    @Test void maximumRadiusGrowsWhenEndpointsAreFartherApart() {
        ConstructionConfig c = new ConstructionConfig();
        c.resizeLines(1);
        var near = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(24, 64, 24, TrackHeading.SOUTH)), c);
        var far = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(48, 64, 48, TrackHeading.SOUTH)), c);
        assertTrue(curve(far.get(0)).getRadius() > curve(near.get(0)).getRadius() + 15);
    }

    @Test void diagonalOffsetsRoundOutward() {
        assertEquals(new Vec3(1, 64, 1), RouteGeometry.curveStart(new BlockPos(0, 64, 0),
            TrackHeading.SOUTH_EAST.vector()));
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.NORTH_EAST),
            point(40, 64, -40, TrackHeading.NORTH_EAST)), new ConstructionConfig());
        double spacing = Math.sqrt(strokes.get(0).start().distSqr(strokes.get(1).start()));
        assertTrue(spacing >= 4);
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
    }

    @Test void slopesMayTurnWhenRadiusAndGradientAllowIt() {
        ConstructionConfig c = new ConstructionConfig();
        var slope = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 68, 0, TrackHeading.EAST)), c);
        assertEquals(2, slope.stream().filter(RouteGeometry.Stroke::curve).count());
        var diagonal = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.SOUTH_EAST),
            point(40, 68, 40, TrackHeading.SOUTH_EAST)), c);
        assertEquals(2, diagonal.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(diagonal, 2, 4));
        for (SurveyPoint end : List.of(point(40, 68, 40, TrackHeading.SOUTH),
            point(50, 68, 30, TrackHeading.SOUTH_EAST))) {
            var turn = RouteGeometry.build(List.of(point(0, 64, 0, TrackHeading.EAST),
                end), c);
            assertEquals(2, turn.stream().filter(RouteGeometry.Stroke::curve).count());
            assertTrue(RailClearance.hasRequiredSpacing(turn, 2, 4));
            for (RouteGeometry.Stroke stroke : turn.stream().filter(RouteGeometry.Stroke::curve).toList()) {
                double middleY = curve(stroke).getPosition(.5).y;
                assertTrue(middleY > 64 && middleY < 68);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(16, 84, 16, TrackHeading.SOUTH)), c));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 68, 5, TrackHeading.EAST)), c));
    }

    @Test void createPermitsThreeHorizontalBlocksPerRiseOnLongSlopes() {
        ConstructionConfig config = new ConstructionConfig();
        var points = List.of(point(0, 64, 0, TrackHeading.EAST),
            point(12, 68, 0, TrackHeading.EAST));
        var strokes = RouteGeometry.build(points, config);
        assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(11, 68, 0, TrackHeading.EAST)), config));
    }

    @Test void climbingTurnUsesTurnRadiusInsteadOfStraightSlopeRule() {
        ConstructionConfig config = new ConstructionConfig();
        var strokes = RouteGeometry.build(List.of(point(0, 64, 0, TrackHeading.EAST),
            point(20, 72, 20, TrackHeading.SOUTH)), config);
        assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
    }

    @Test void optionalUnlimitedPlacementPolicyCanRelaxOnlyThePlacementLimit() {
        ConstructionConfig config = new ConstructionConfig();
        var points = List.of(point(0, 64, 0, TrackHeading.EAST),
            point(8, 68, 0, TrackHeading.EAST));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.buildLayout(points, config));
        var layout = RouteGeometry.buildLayout(points, config, true);
        assertTrue(RailClearance.hasRequiredSpacing(layout.strokes(), 2, 4));
    }

    @Test void overlappingAndTooTightTurnsAreRejected() {
        ConstructionConfig c = new ConstructionConfig();
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(4, 64, 4, TrackHeading.SOUTH)), c));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(40, 64, 0, TrackHeading.WEST)), c));
    }

    @Test void parallelButOffsetEndsFormASCurve() {
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST), point(50, 64, 6, TrackHeading.EAST)),
            new ConstructionConfig());
        assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
        assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4));
    }

    @Test void intermediatePointIsAnActualRailEndWithOneSharedDirection() {
        ConstructionConfig c = new ConstructionConfig();
        c.resizeLines(1);
        var strokes = RouteGeometry.build(List.of(
            point(0, 64, 0, TrackHeading.EAST),
            point(30, 64, 0, TrackHeading.EAST),
            point(60, 64, 30, TrackHeading.SOUTH)), c);
        assertEquals(2, strokes.size());
        assertEquals(strokes.get(0).end(), strokes.get(1).start());
        assertEquals(new BlockPos(30, 64, 0), strokes.get(0).end());
    }

    @Test void multipleSegmentsJoinAtAnOrientedIntermediateRailEnd() {
        ConstructionConfig c = new ConstructionConfig();
        var layout = RouteGeometry.buildLayout(List.of(
            point(0, 64, 0, TrackHeading.EAST),
            point(30, 64, 0, TrackHeading.EAST),
            point(60, 64, 30, TrackHeading.SOUTH)), c);
        assertTrue(RailClearance.hasRequiredSpacing(layout.strokes(), 2, 4));
        for (int line = 0; line < 2; line++) {
            int selectedLine = line;
            var route = layout.strokes().stream().filter(s -> s.line() == selectedLine).toList();
            assertEquals(route.get(0).end(), route.get(1).start());
        }
    }

    @Test void centerOfExistingPairAndHeadingRotation() {
        BlockPos center = new BlockPos(-493, 58, 318);
        assertEquals(center, ParallelTrackAlignment.center(center.north(2), new Vec3(1, 0, 0), 3,
            p -> p.equals(center.south(2))));
        assertEquals(center, ParallelTrackAlignment.center(center.north(3), new Vec3(1, 0, 0), 3,
            p -> p.equals(center.south(3))));
        assertEquals(TrackHeading.SOUTH_EAST, TrackHeading.EAST.rotate(1));
        assertEquals(TrackHeading.NORTH_EAST, TrackHeading.EAST.rotate(-1));
        assertEquals(TrackHeading.NORTH, TrackHeading.fromVector(new Vec3(0.1, 0, -3)));
    }

    @Test void railClearanceRejectsGeometricallyCloseLines() {
        var first = new RouteGeometry.Stroke(new BlockPos(0, 64, 0), new BlockPos(40, 64, 0),
            new Vec3(1, 0, 0), new Vec3(1, 0, 0), false, 0);
        var second = new RouteGeometry.Stroke(new BlockPos(0, 64, 3), new BlockPos(40, 64, 3),
            new Vec3(1, 0, 0), new Vec3(1, 0, 0), false, 1);
        assertFalse(RailClearance.hasRequiredSpacing(List.of(first, second), 2, 4));
    }

    @Test void allEightHeadingsKeepClearanceOnBothTurnSides() {
        for (TrackHeading first : TrackHeading.values()) {
            for (int turn : new int[] {-2, -1, 1, 2}) {
                TrackHeading second = first.rotate(turn);
                BlockPos destination = new BlockPos((first.dx + second.dx) * 30, 64,
                    (first.dz + second.dz) * 30);
                ConstructionConfig config = new ConstructionConfig();
                List<RouteGeometry.Stroke> strokes = RouteGeometry.build(List.of(
                    new SurveyPoint(new BlockPos(0, 64, 0), first),
                    new SurveyPoint(destination, second)), config);
                assertTrue(RailClearance.hasRequiredSpacing(strokes, 2, 4), first + " to " + second);
                assertEquals(2, strokes.stream().filter(RouteGeometry.Stroke::curve).count(),
                    first + " to " + second);
            }
        }
    }

    @Test void fourParallelTracksRemainSeparatedThroughFortyFiveAndNinetyTurns() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(4);
        for (TrackHeading end : List.of(TrackHeading.SOUTH_EAST, TrackHeading.SOUTH)) {
            BlockPos destination = new BlockPos((1 + end.dx) * 50, 64, end.dz * 50);
            var strokes = RouteGeometry.build(List.of(
                point(0, 64, 0, TrackHeading.EAST), new SurveyPoint(destination, end)), config);
            assertEquals(4, strokes.stream().filter(RouteGeometry.Stroke::curve).count());
            assertTrue(RailClearance.hasRequiredSpacing(strokes, 4, 4));
        }
    }

    @Test void roadbedCoversEveryCurvedRailAndKeepsNineWideStraightSection() {
        ConstructionConfig config = new ConstructionConfig();
        for (SurveyPoint destination : List.of(
            point(40, 64, 0, TrackHeading.EAST),
            point(50, 64, 30, TrackHeading.SOUTH_EAST),
            point(40, 64, 40, TrackHeading.SOUTH))) {
            var layout = RouteGeometry.buildLayout(List.of(point(0, 64, 0, TrackHeading.EAST), destination), config);
            List<RoadbedGeometry.Sample> samples = new ArrayList<>();
            for (RouteGeometry.Stroke stroke : layout.strokes()) {
                if (!stroke.curve()) {
                    int steps = (int) Math.ceil(Math.sqrt(stroke.start().distSqr(stroke.end())) * 2);
                    for (int step = 0; step <= steps; step++) {
                        BlockPos cell = BlockPos.containing(Vec3.atCenterOf(stroke.start())
                            .lerp(Vec3.atCenterOf(stroke.end()), step / (double) steps));
                        samples.add(new RoadbedGeometry.Sample(Vec3.atCenterOf(cell), stroke.startAxis(), stroke.line()));
                    }
                } else {
                    RoadbedGeometry.addCurveSamples(samples, stroke.start(), stroke.startAxis(),
                        curve(stroke), stroke.end(), stroke.endAxis(), stroke.line());
                }
            }
            Set<BlockPos> floor = RoadbedGeometry.rasterize(samples, config, layout.effectiveGap());
            for (RoadbedGeometry.Sample sample : samples)
                assertTrue(floor.contains(BlockPos.containing(sample.center().add(0, -1.125, 0))));
            for (RouteGeometry.Stroke stroke : layout.strokes()) {
                for (BlockPos endpoint : List.of(stroke.start(), stroke.end())) {
                    Vec3 tangent = endpoint.equals(stroke.start()) ? stroke.startAxis() : stroke.endAxis();
                    Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();
                    for (int quarter = -config.edgeMargin * 4; quarter <= layout.effectiveGap() * 2; quarter++) {
                        Vec3 position = Vec3.atCenterOf(endpoint).add(normal.scale(quarter / 4.0));
                        BlockPos expected = BlockPos.containing(position.add(0, -1.125, 0));
                        assertTrue(floor.contains(expected), "missing endpoint roadbed at " + expected);
                    }
                }
            }
            if (destination.heading() == TrackHeading.EAST) {
                for (int x = 3; x < 37; x++) {
                    int column = x;
                    assertEquals(9, floor.stream().filter(p -> p.getX() == column && p.getY() == 63).count());
                }
            }
            Set<BlockPos> reached = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(floor.iterator().next());
            while (!queue.isEmpty()) {
                BlockPos p = queue.removeFirst();
                if (!reached.add(p)) continue;
                for (BlockPos next : List.of(p.north(), p.south(), p.east(), p.west()))
                    if (floor.contains(next) && !reached.contains(next)) queue.add(next);
            }
            assertEquals(floor.size(), reached.size(), "Roadbed separated near " + destination);
        }
    }

    private static BezierConnection curve(RouteGeometry.Stroke stroke) {
        return new BezierConnection(Couple.create(stroke.start(), stroke.end()),
            Couple.create(RouteGeometry.curveStart(stroke.start(), stroke.startAxis()),
                RouteGeometry.curveStart(stroke.end(), stroke.endAxis().scale(-1))),
            Couple.create(stroke.startAxis(), stroke.endAxis().scale(-1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
    }
}
