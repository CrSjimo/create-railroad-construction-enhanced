package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

final class ConstructionPlannerTest {
    private static final BlockPos FLOOR = new BlockPos(4, 63, 0);
    private static final BlockPos SIDE_FLOOR = FLOOR.south();
    private static final BlockPos WALL = new BlockPos(4, 64, 2);
    private static final BlockPos ROOF = new BlockPos(4, 70, 0);
    private static final List<SurveyPoint> POINTS = List.of(
        new SurveyPoint(new BlockPos(0, 64, 0), TrackHeading.EAST),
        new SurveyPoint(new BlockPos(8, 64, 0), TrackHeading.EAST));

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        dev.sjimo.rrce.TestBootstrap.bootStrap();
    }

    private static ConstructionConfig config() {
        ConstructionConfig config = new ConstructionConfig();
        config.resizeLines(1);
        config.edgeMargin = 0;
        config.tunnelSideClearance = 1;
        config.allBlocksTerrain = true;
        config.materials.set(0, "");
        return config;
    }

    private static ServerLevel level() {
        // Avoid JVM agent attachment; these world methods do not require final-method mocking.
        ServerLevel level = mock(ServerLevel.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        when(level.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(level.getMaxBuildHeight()).thenReturn(128);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        return level;
    }

    @Test void unsetMaterialsSkipFloorsWallsAndRoofButStillExcavateTheTunnel() {
        ConstructionConfig config = config();
        config.foundation = "";
        config.wall = "";
        var plan = ConstructionPlanner.plan(level(), POINTS, config);
        assertFalse(plan.blocks.containsKey(FLOOR));
        assertFalse(plan.blocks.containsKey(SIDE_FLOOR));
        assertFalse(plan.blocks.containsKey(WALL));
        assertFalse(plan.blocks.containsKey(ROOF));
        assertTrue(plan.blocks.get(FLOOR.above()).isAir());
        assertTrue(plan.blocks.get(SIDE_FLOOR.above()).isAir());
    }

    @Test void explicitAirClearsTunnelFloorsWallsAndRoof() {
        ConstructionConfig config = config();
        config.foundation = "minecraft:air";
        config.wall = "minecraft:air";
        var plan = ConstructionPlanner.plan(level(), POINTS, config);
        for (BlockPos pos : List.of(FLOOR, SIDE_FLOOR, WALL, ROOF))
            assertEquals(Blocks.AIR.defaultBlockState(), plan.blocks.get(pos), pos.toShortString());
    }

    @Test void foundationAndWallCanBeDisabledIndependently() {
        ConstructionConfig config = config();
        config.foundation = "";
        var wallsOnly = ConstructionPlanner.plan(level(), POINTS, config);
        assertFalse(wallsOnly.blocks.containsKey(FLOOR));
        assertFalse(wallsOnly.blocks.containsKey(SIDE_FLOOR));
        assertEquals(Blocks.STONE.defaultBlockState(), wallsOnly.blocks.get(WALL));
        assertEquals(Blocks.STONE.defaultBlockState(), wallsOnly.blocks.get(ROOF));
        config.foundation = "minecraft:stone_brick_slab";
        config.wall = "";
        var foundationOnly = ConstructionPlanner.plan(level(), POINTS, config);
        assertTrue(foundationOnly.blocks.get(FLOOR).is(Blocks.STONE_BRICK_SLAB));
        assertTrue(foundationOnly.blocks.get(SIDE_FLOOR).is(Blocks.STONE_BRICK_SLAB));
        assertFalse(foundationOnly.blocks.containsKey(WALL));
        assertFalse(foundationOnly.blocks.containsKey(ROOF));
    }

    @Test void materialSelectionStillAppliesWhenTerrainWorkIsOff() {
        ConstructionConfig config = config();
        config.terrainWork = false;
        config.foundation = "";
        config.wall = "";
        assertTrue(ConstructionPlanner.plan(level(), POINTS, config).blocks.isEmpty());
        config.foundation = "minecraft:air";
        var plan = ConstructionPlanner.plan(level(), POINTS, config);
        assertTrue(plan.blocks.containsKey(FLOOR));
        assertTrue(plan.blocks.values().stream().allMatch(state -> state.isAir()));
        assertFalse(plan.blocks.containsKey(FLOOR.above()));
    }

    @Test void unsetFoundationDoesNotValidateOrReplaceProtectedTunnelFloor() {
        ConstructionConfig config = config();
        config.allBlocksTerrain = false;
        config.foundation = "";
        config.wall = "";
        ServerLevel level = level();
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.getY() == 63) return Blocks.BEDROCK.defaultBlockState();
            return pos.getY() >= 64 && pos.getY() <= 80 ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
        });
        var plan = ConstructionPlanner.plan(level, POINTS, config);
        assertFalse(plan.blocks.containsKey(FLOOR));
        assertFalse(plan.blocks.containsKey(SIDE_FLOOR));
        config.foundation = "minecraft:air";
        assertThrows(UserFacingException.class, () -> ConstructionPlanner.plan(level, POINTS, config));
    }
}
