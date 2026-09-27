package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TunnelGeometryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void bothRailwayPortalsStayOpenWhileSideWallsRemain() {
        Set<BlockPos> interior = new HashSet<>();
        Set<BlockPos> roadbed = new HashSet<>();
        for (int x = 0; x <= 20; x++) for (int z = -4; z <= 4; z++) {
            BlockPos floor = new BlockPos(x, 63, z);
            roadbed.add(floor);
            if (x >= 5 && x <= 15) interior.add(floor);
        }
        Set<BlockPos> walls = TunnelGeometry.wallColumns(interior, roadbed, 2);
        for (int z = -4; z <= 4; z++) {
            assertFalse(walls.contains(new BlockPos(4, 63, z)), "entrance was sealed");
            assertFalse(walls.contains(new BlockPos(16, 63, z)), "exit was sealed");
        }
        assertTrue(walls.contains(new BlockPos(10, 63, 5)));
        assertTrue(walls.contains(new BlockPos(10, 63, -6)));

        roadbed.remove(new BlockPos(16, 63, 0));
        roadbed.add(new BlockPos(16, 64, 0));
        assertFalse(TunnelGeometry.wallColumns(interior, roadbed, 2)
            .contains(new BlockPos(16, 63, 0)), "sloped exit was sealed");
    }

    @Test void terminalWallCapsAreOmittedWithoutExcavatingOutsideTheRoute() {
        Set<BlockPos> roadbed = new HashSet<>();
        for (int x = 0; x <= 20; x++) for (int z = -4; z <= 4; z++)
            roadbed.add(new BlockPos(x, 63, z));
        Set<BlockPos> tunnel = new HashSet<>(roadbed);
        List<SurveyPoint> route = List.of(
            new SurveyPoint(new BlockPos(0, 64, 0), TrackHeading.EAST),
            new SurveyPoint(new BlockPos(20, 64, 0), TrackHeading.EAST));
        Set<BlockPos> openings = TunnelGeometry.terminalWallOpenings(roadbed, tunnel, route, 4);
        assertTrue(openings.contains(new BlockPos(-3, 63, 0)));
        assertTrue(openings.contains(new BlockPos(23, 63, 0)));
        Set<BlockPos> wallExclusions = new HashSet<>(roadbed);
        wallExclusions.addAll(openings);
        assertFalse(TunnelGeometry.wallColumns(tunnel, wallExclusions, 2).contains(new BlockPos(-1, 63, 0)));
        assertFalse(TunnelGeometry.wallColumns(tunnel, wallExclusions, 2).contains(new BlockPos(21, 63, 0)));
        assertTrue(TunnelGeometry.wallColumns(tunnel, wallExclusions, 2).contains(new BlockPos(10, 63, 5)));

        ConstructionPlan plan = new ConstructionPlan();
        for (BlockPos floor : List.of(new BlockPos(0, 63, 0), new BlockPos(20, 63, 0),
            new BlockPos(10, 63, 5)))
            for (int h = 1; h <= 6; h++) plan.put(floor.above(h), Blocks.STONE.defaultBlockState());
        TunnelGeometry.reopenPassage(plan, roadbed, Set.of(), Set.of(), 6);
        for (int h = 1; h <= 6; h++) {
            assertTrue(plan.blocks.get(new BlockPos(0, 63 + h, 0)).isAir());
            assertTrue(plan.blocks.get(new BlockPos(20, 63 + h, 0)).isAir());
            assertFalse(plan.blocks.get(new BlockPos(10, 63 + h, 5)).isAir());
            assertFalse(plan.blocks.containsKey(new BlockPos(-1, 63 + h, 0)));
            assertFalse(plan.blocks.containsKey(new BlockPos(21, 63 + h, 0)));
        }
    }
}
