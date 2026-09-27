package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.utility.Couple;
import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CutGeometryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void straightCutWidensOnlyAcrossTheTrackAndKeepsBothEndsFlat() {
        Map<BlockPos, Integer> heights = new HashMap<>();
        Map<BlockPos, Vec3> tangents = new HashMap<>();
        for (int x = 0; x <= 10; x++) for (int z = -4; z <= 4; z++) {
            BlockPos floor = new BlockPos(x, 63, z);
            heights.put(floor, 4);
            tangents.put(floor, new Vec3(1, 0, 0));
        }
        Set<BlockPos> cut = new HashSet<>();
        CutGeometry.forEachCell(heights, tangents, Math.toDegrees(Math.atan(2)),
            List.of(new SurveyPoint(new BlockPos(0, 64, 0), TrackHeading.EAST),
                new SurveyPoint(new BlockPos(10, 64, 0), TrackHeading.EAST)), cut::add);
        assertTrue(cut.contains(new BlockPos(5, 67, 6)), "side slope is missing");
        assertTrue(cut.contains(new BlockPos(5, 67, -6)), "other side slope is missing");
        assertFalse(cut.stream().anyMatch(p -> p.getX() < 0 || p.getX() > 10),
            "cut extends past a track endpoint");
    }

    @Test void diagonalCutRespectsTheEndpointPlanesAfterBlockRounding() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        config.edgeMargin = 2;
        Vec3 tangent = TrackHeading.SOUTH_EAST.vector();
        List<RoadbedGeometry.Sample> samples = new ArrayList<>();
        for (int step = 0; step <= 80; step++)
            samples.add(new RoadbedGeometry.Sample(new Vec3(.5 + step / 4.0, 64.5,
                .5 + step / 4.0), tangent, 0));
        var pavement = RoadbedGeometry.foundationLayout(samples, config, 4);
        Map<BlockPos, Integer> heights = new HashMap<>();
        for (BlockPos floor : pavement.floors()) heights.put(floor, 4);
        Set<BlockPos> cut = new HashSet<>();
        BlockPos start = new BlockPos(0, 64, 0), end = new BlockPos(20, 64, 20);
        CutGeometry.forEachCell(heights, pavement.tangents(), Math.toDegrees(Math.atan(2)),
            List.of(new SurveyPoint(start, TrackHeading.SOUTH_EAST),
                new SurveyPoint(end, TrackHeading.SOUTH_EAST)), cut::add);
        Vec3 startCenter = Vec3.atCenterOf(start), endCenter = Vec3.atCenterOf(end);
        assertFalse(cut.stream().anyMatch(p -> Vec3.atCenterOf(p).subtract(startCenter).dot(tangent) < -1e-6),
            "diagonal start cap extends behind its endpoint");
        assertFalse(cut.stream().anyMatch(p -> Vec3.atCenterOf(p).subtract(endCenter).dot(tangent) > 1e-6),
            "diagonal end cap extends beyond its endpoint");
        assertTrue(cut.size() > pavement.floors().size(), "side slope did not expand");
    }

    @Test void turningCutDoesNotWrapAroundItsTerminalFaces() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        config.edgeMargin = 2;
        BlockPos start = new BlockPos(0, 64, 0), end = new BlockPos(40, 64, 40);
        Vec3 east = TrackHeading.EAST.vector(), south = TrackHeading.SOUTH.vector();
        BezierConnection curve = new BezierConnection(Couple.create(start, end),
            Couple.create(RouteGeometry.curveStart(start, east), RouteGeometry.curveStart(end, south.scale(-1))),
            Couple.create(east, south.scale(-1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), true, false, null);
        List<RoadbedGeometry.Sample> samples = new ArrayList<>();
        RoadbedGeometry.addCurveSamples(samples, start, east, curve, end, south, 0);
        var pavement = RoadbedGeometry.foundationLayout(samples, config, 4);
        Map<BlockPos, Integer> heights = new HashMap<>();
        for (BlockPos floor : pavement.floors()) heights.put(floor, 4);
        Set<BlockPos> cut = new HashSet<>();
        CutGeometry.forEachCell(heights, pavement.tangents(), Math.toDegrees(Math.atan(2)),
            List.of(new SurveyPoint(start, TrackHeading.EAST),
                new SurveyPoint(end, TrackHeading.SOUTH)), cut::add);
        assertFalse(cut.stream().anyMatch(p -> p.getX() < 0), "start of bend was excavated backwards");
        assertFalse(cut.stream().anyMatch(p -> p.getZ() > 40), "end of bend was excavated forwards");
    }
}
