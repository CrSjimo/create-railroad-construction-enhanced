package dev.sjimo.rrce.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.sjimo.rrce.platform.CreateCouple;
import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Builds a complete immutable-in-practice plan before the world is edited. */
public final class ConstructionPlanner {
    private ConstructionPlanner() {}

    public static ConstructionPlan plan(ServerLevel level, List<SurveyPoint> points, ConstructionConfig config) {
        return plan(level, points, config, false);
    }

    public static ConstructionPlan plan(ServerLevel level, List<SurveyPoint> points, ConstructionConfig config,
                                        boolean relaxedPlacementChecks) {
        if (points.size() < 2) throw new UserFacingException("error.rrce.need_two_points");
        if (points.size() > 64) throw new UserFacingException("error.rrce.too_many_points");
        if (config.spacing < 1 || config.spacing > 32) throw new UserFacingException("error.rrce.invalid_spacing");
        TerrainRules.RuleSet terrainRules = TerrainRules.current();
        Block foundation = requireSolid(config.foundation, "screen.rrce.field.foundation");
        BlockState foundationState = config.foundationState();
        Block wall = requireSolid(config.wall, "screen.rrce.field.wall");
        ConstructionPlan plan = new ConstructionPlan();
        Map<BlockPos, BlockState> trackStates = new HashMap<>();
        List<RoadbedGeometry.Sample> samples = new ArrayList<>();
        RouteGeometry.Layout layout = RouteGeometry.buildLayout(points, config, relaxedPlacementChecks);
        for (RouteGeometry.Stroke stroke : layout.strokes()) {
            int line = stroke.line();
            String materialId = config.materials.get(line);
            boolean buildTrack = !materialId.isBlank();
            TrackMaterial material = buildTrack ? TrackMaterial.ALL.get(ResourceLocation.tryParse(materialId)) : null;
            if (buildTrack && material == null) throw new UserFacingException("error.rrce.unknown_track", materialId);
            BlockPos a = stroke.start(), b = stroke.end();
            Vec3 axisA = stroke.startAxis(), axisB = stroke.endAxis();
            if (a.distSqr(b) > 512 * 512) throw new UserFacingException("error.rrce.segment_too_long");
            if (buildTrack) {
                markTrack(level, trackStates, a, material, axisA, stroke.curve(), config.terrainWork);
                markTrack(level, trackStates, b, material, axisB, stroke.curve(), config.terrainWork);
            }
            if (!stroke.curve()) {
                int steps = (int) Math.ceil(Math.sqrt(a.distSqr(b)) * 2);
                for (int s = 0; s <= steps; s++) {
                    BlockPos p = BlockPos.containing(Vec3.atCenterOf(a).lerp(Vec3.atCenterOf(b), s / (double) steps));
                    if (buildTrack) markTrack(level, trackStates, p, material, axisA, false, config.terrainWork);
                    samples.add(new RoadbedGeometry.Sample(Vec3.atCenterOf(p), axisA, line));
                }
            } else {
                // A disabled line still needs the same curve and clearance checks and roadbed sweep.
                TrackMaterial geometryMaterial = buildTrack ? material : TrackMaterial.ANDESITE;
                BlockState startState = geometryMaterial.getBlock().defaultBlockState().setValue(TrackBlock.SHAPE, shape(axisA));
                BlockState endState = geometryMaterial.getBlock().defaultBlockState().setValue(TrackBlock.SHAPE, shape(axisB));
                BezierConnection curve = new BezierConnection(CreateCouple.create(a, b),
                    CreateCouple.create(geometryMaterial.getBlock().getCurveStart(level, a, startState, rawAxis(startState, axisA)),
                        geometryMaterial.getBlock().getCurveStart(level, b, endState, rawAxis(endState, axisB.scale(-1)))),
                    CreateCouple.create(axisA, axisB.scale(-1)),
                    CreateCouple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
                    config.primaryAtStart.get(line), false, geometryMaterial);
                double turnDegrees = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, axisA.dot(axisB)))));
                if (turnDegrees > 90.01 || !relaxedPlacementChecks && turnDegrees > .1
                    && curve.getRadius() < (turnDegrees > 60 ? 7 : 3.25))
                    throw new UserFacingException("error.rrce.curve_radius_between", a.toShortString(), b.toShortString());
                if (buildTrack) {
                    if (level.getBlockEntity(a) instanceof TrackBlockEntity existingA)
                        checkExistingCurve(existingA, b, curve);
                    if (level.getBlockEntity(b) instanceof TrackBlockEntity existingB)
                        checkExistingCurve(existingB, a, curve.secondary());
                    plan.curves.add(curve);
                }
                RoadbedGeometry.addCurveSamples(samples, a, axisA, curve, b, axisB, line);
            }
        }
        Set<BlockPos> trackCells = trackStates.keySet();
        RoadbedGeometry.FoundationLayout pavement = RoadbedGeometry.foundationLayout(samples, config,
            layout.effectiveGap());
        Set<BlockPos> corridor = new HashSet<>(pavement.floors());
        Map<BlockPos, BlockState> foundationBlocks = new HashMap<>(pavement.blocks());
        Set<Long> supportedColumns = new HashSet<>();
        for (BlockPos floor : corridor)
            supportedColumns.add(((long) floor.getX() << 32) ^ (floor.getZ() & 0xffffffffL));
        for (BlockPos track : trackCells) {
            BlockPos below = track.below();
            long column = ((long) below.getX() << 32) ^ (below.getZ() & 0xffffffffL);
            if (supportedColumns.add(column)) {
                corridor.add(below);
                foundationBlocks.put(below, foundationState);
            }
        }
        Map<BlockPos, Integer> obstacleHeights = new HashMap<>();
        Set<BlockPos> tunnelFloors = new HashSet<>();
        Map<BlockPos, Integer> cutHeights = new HashMap<>();
        if (config.terrainWork) {
            for (BlockPos floor : corridor) {
                int height = 0;
                for (int y = floor.getY() + 1; y <= Math.min(level.getMaxBuildHeight() - 1, floor.getY() + 64); y++) {
                    BlockPos p = new BlockPos(floor.getX(), y, floor.getZ());
                    if (!level.hasChunkAt(p)) throw new UserFacingException("error.rrce.chunk_unloaded", p.toShortString());
                    if (terrainRules.isTerrain(level.getBlockState(p), config.allBlocksTerrain))
                        height = Math.max(height, y - floor.getY());
                }
                if (height > 0) obstacleHeights.put(floor, height);
            }
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos seed : obstacleHeights.keySet()) {
                if (!seen.add(seed)) continue;
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                List<BlockPos> component = new ArrayList<>();
                int highest = 0;
                queue.add(seed);
                while (!queue.isEmpty()) {
                    BlockPos current = queue.removeFirst();
                    component.add(current);
                    highest = Math.max(highest, obstacleHeights.get(current));
                    for (BlockPos next : new BlockPos[] {current.north(), current.south(), current.east(), current.west()})
                        if (obstacleHeights.containsKey(next) && seen.add(next)) queue.add(next);
                }
                if (highest > config.obstacleThreshold) tunnelFloors.addAll(component);
                else for (BlockPos p : component) cutHeights.put(p, highest);
            }
        }
        Set<BlockPos> wallExclusions = new HashSet<>(corridor);
        wallExclusions.addAll(TunnelGeometry.terminalWallOpenings(corridor, tunnelFloors, points,
            config.wallThickness + config.tunnelSideClearance + 2));
        for (BlockPos floor : corridor) {
            if (!level.hasChunkAt(floor)) throw new UserFacingException("error.rrce.chunk_unloaded", floor.toShortString());
            if (config.terrainWork) {
                for (int h = 1; h <= config.tunnelHeight; h++) {
                    BlockPos p = floor.above(h);
                    if (trackCells.contains(p)) continue;
                    BlockState old = level.getBlockState(p);
                    if (!terrainRules.isRemovable(old, config.allBlocksTerrain))
                        throw new UserFacingException("error.rrce.protected_block", p.toShortString());
                    if (!old.isAir()) plan.put(p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (config.terrainWork) {
            buildTunnel(level, plan, tunnelFloors, wallExclusions, trackCells, foundationState, wall,
                config, terrainRules);
            buildCut(level, plan, cutHeights, pavement.tangents(), trackCells, config, points, terrainRules);
            // Reopen only the surveyed railway. Outside it, natural terrain stays intact.
            TunnelGeometry.reopenPassage(plan, corridor, trackCells, foundationBlocks.keySet(),
                config.tunnelHeight);
        }
        for (Map.Entry<BlockPos, BlockState> entry : foundationBlocks.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState desired = entry.getValue();
            if (trackCells.contains(p))
                throw new UserFacingException("error.rrce.foundation_track_overlap", p.toShortString());
            if (!level.hasChunkAt(p)) throw new UserFacingException("error.rrce.chunk_unloaded", p.toShortString());
            BlockState old = level.getBlockState(p);
            if (old.equals(desired)) {
                if (plan.blocks.containsKey(p)) plan.put(p, desired);
                continue;
            }
            boolean wasExcavated = plan.blocks.containsKey(p) && plan.blocks.get(p).isAir();
            if (config.replaceFoundation || old.canBeReplaced() || wasExcavated) {
                if (!terrainRules.isRemovable(old, config.allBlocksTerrain))
                    throw new UserFacingException("error.rrce.protected_block", p.toShortString());
                plan.put(p, desired);
            } else if (pavement.floors().contains(p) && old.isCollisionShapeFullBlock(level, p)) {
                // A complete existing base still supports the raised half slab.
                plan.blocks.remove(p);
            } else if (pavement.floors().contains(p) && old.hasProperty(SlabBlock.TYPE)
                && desired.hasProperty(SlabBlock.TYPE)
                && old.getValue(SlabBlock.TYPE) == desired.getValue(SlabBlock.TYPE)) {
                plan.blocks.remove(p);
            } else {
                throw new UserFacingException("error.rrce.replace_foundation_required", p.toShortString());
            }
        }
        // Track wins over clearing and roadbed operations at the same cell.
        for (Map.Entry<BlockPos, BlockState> entry : trackStates.entrySet()) plan.put(entry.getKey(), entry.getValue());
        return plan;
    }

    private static void markTrack(ServerLevel level, Map<BlockPos, BlockState> tracks, BlockPos pos,
                                  TrackMaterial material, Vec3 axis, boolean bezier, boolean terrainWork) {
        if (!level.hasChunkAt(pos)) throw new UserFacingException("error.rrce.chunk_unloaded", pos.toShortString());
        TrackShape shape = shape(axis);
        BlockState old = level.getBlockState(pos);
        BlockState desired = material.getBlock().defaultBlockState().setValue(TrackBlock.SHAPE, shape)
            .setValue(TrackBlock.HAS_BE, bezier || old.hasProperty(TrackBlock.HAS_BE) && old.getValue(TrackBlock.HAS_BE));
        if (old.getBlock() instanceof TrackBlock && (!old.is(material.getBlock()) || old.getValue(TrackBlock.SHAPE) != shape))
            throw new UserFacingException("error.rrce.existing_track_conflict", pos.toShortString());
        if (old.getBlock() instanceof TrackBlock)
            desired = bezier ? old.setValue(TrackBlock.HAS_BE, true) : old;
        if (!old.canBeReplaced() && !(old.getBlock() instanceof TrackBlock) && !terrainWork)
            throw new UserFacingException("error.rrce.track_occupied", pos.toShortString());
        BlockState prior = tracks.get(pos);
        if (prior != null && (!prior.is(material.getBlock()) || prior.getValue(TrackBlock.SHAPE) != shape))
            throw new UserFacingException("error.rrce.planned_track_conflict", pos.toShortString());
        if (prior != null && prior.getValue(TrackBlock.HAS_BE)) desired = desired.setValue(TrackBlock.HAS_BE, true);
        tracks.put(pos.immutable(), desired);
        if (tracks.size() > 100_000) throw new UserFacingException("error.rrce.track_limit");
    }

    private static void checkExistingCurve(TrackBlockEntity entity, BlockPos other, BezierConnection expected) {
        BezierConnection existing = entity.getConnections().get(other);
        if (existing != null && (!existing.equalsSansMaterial(expected)
            || !existing.getMaterial().id.equals(expected.getMaterial().id)))
            throw new UserFacingException("error.rrce.existing_curve_conflict", entity.getBlockPos().toShortString());
    }

    private static void buildTunnel(ServerLevel level, ConstructionPlan plan, Set<BlockPos> tunnelFloors,
                                    Set<BlockPos> corridor,
                                    Set<BlockPos> trackCells, BlockState foundationState, Block wall,
                                    ConstructionConfig config, TerrainRules.RuleSet terrainRules) {
        if (tunnelFloors.isEmpty()) return;
        Set<BlockPos> interior = new HashSet<>(tunnelFloors);
        for (BlockPos floor : tunnelFloors) for (int dx = -config.tunnelSideClearance; dx <= config.tunnelSideClearance; dx++)
            for (int dz = -config.tunnelSideClearance; dz <= config.tunnelSideClearance; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= config.tunnelSideClearance) interior.add(floor.offset(dx, 0, dz));
        for (BlockPos floor : interior) {
            if (!level.hasChunkAt(floor)) throw new UserFacingException("error.rrce.chunk_unloaded", floor.toShortString());
            BlockState base = level.getBlockState(floor);
            if (config.replaceFoundation && !terrainRules.isRemovable(base, config.allBlocksTerrain))
                throw new UserFacingException("error.rrce.protected_block", floor.toShortString());
            if (config.replaceFoundation || base.canBeReplaced()) plan.put(floor, foundationState);
            else if (!base.isCollisionShapeFullBlock(level, floor))
                throw new UserFacingException("error.rrce.tunnel_floor_replace_required", floor.toShortString());
            for (int h = 1; h <= config.tunnelHeight; h++) {
                BlockPos p = floor.above(h);
                if (trackCells.contains(p)) continue;
                ensureRemovable(level, p, config, terrainRules);
                if (!level.getBlockState(p).isAir()) plan.put(p, Blocks.AIR.defaultBlockState());
            }
            for (int h = 1; h <= config.roofThickness; h++) {
                BlockPos p = floor.above(config.tunnelHeight + h);
                ensureRemovable(level, p, config, terrainRules);
                plan.put(p, wall.defaultBlockState());
            }
        }
        for (BlockPos floor : TunnelGeometry.wallColumns(interior, corridor, config.wallThickness)) {
            for (int h = 1; h <= config.tunnelHeight + config.roofThickness; h++) {
                BlockPos p = floor.above(h);
                if (trackCells.contains(p)) continue;
                ensureRemovable(level, p, config, terrainRules);
                plan.put(p, wall.defaultBlockState());
            }
        }
    }

    private static void buildCut(ServerLevel level, ConstructionPlan plan, Map<BlockPos, Integer> heights,
                                 Map<BlockPos, Vec3> tangents, Set<BlockPos> trackCells,
                                 ConstructionConfig config, List<SurveyPoint> points,
                                 TerrainRules.RuleSet terrainRules) {
        CutGeometry.forEachCell(heights, tangents, config.cutAngle, points, p -> {
            if (!level.hasChunkAt(p)) throw new UserFacingException("error.rrce.chunk_unloaded", p.toShortString());
            if (trackCells.contains(p)) return;
            BlockState old = level.getBlockState(p);
            if (old.isAir()) return;
            if (!terrainRules.isRemovable(old, config.allBlocksTerrain))
                throw new UserFacingException("error.rrce.protected_block", p.toShortString());
            plan.put(p, Blocks.AIR.defaultBlockState());
        });
    }

    private static void ensureRemovable(ServerLevel level, BlockPos p, ConstructionConfig config,
                                        TerrainRules.RuleSet terrainRules) {
        if (!level.hasChunkAt(p)) throw new UserFacingException("error.rrce.chunk_unloaded", p.toShortString());
        if (!terrainRules.isRemovable(level.getBlockState(p), config.allBlocksTerrain))
            throw new UserFacingException("error.rrce.protected_block", p.toShortString());
    }

    private static Block requireSolid(String id, String label) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key))
            throw new UserFacingException("error.rrce.unknown_block", net.minecraft.network.chat.Component.translatable(label), id);
        Block block = BuiltInRegistries.BLOCK.get(key);
        if (!block.defaultBlockState().blocksMotion())
            throw new UserFacingException("error.rrce.block_not_solid", net.minecraft.network.chat.Component.translatable(label), id);
        return block;
    }

    private static TrackShape shape(Vec3 direction) {
        if (Math.abs(direction.x) > .9) return TrackShape.XO;
        if (Math.abs(direction.z) > .9) return TrackShape.ZO;
        return direction.x * direction.z > 0 ? TrackShape.PD : TrackShape.ND;
    }

    private static Vec3 rawAxis(BlockState trackState, Vec3 toward) {
        Vec3 axis = trackState.getValue(TrackBlock.SHAPE).getAxes().get(0);
        return axis.dot(toward) < 0 ? axis.scale(-1) : axis;
    }

}
