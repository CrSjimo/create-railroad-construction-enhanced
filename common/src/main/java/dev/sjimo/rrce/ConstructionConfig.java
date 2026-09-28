package dev.sjimo.rrce;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class ConstructionConfig {
    public int lineCount = 2;
    /** Number of clear block columns between adjacent tracks. */
    public int spacing = 3;
    public int edgeMargin = 2;
    public int obstacleThreshold = 8;
    public int tunnelHeight = 6;
    public int tunnelSideClearance = 0;
    public int wallThickness = 1;
    public int roofThickness = 1;
    public double cutAngle = Math.toDegrees(Math.atan(2));
    public boolean replaceFoundation = true;
    public boolean terrainWork = true;
    public boolean allBlocksTerrain;
    public String foundation = "minecraft:stone_brick_slab";
    public String wall = "minecraft:stone";
    public final List<String> materials = new ArrayList<>();
    public final List<Boolean> primaryAtStart = new ArrayList<>();

    public ConstructionConfig() {
        resizeLines(2);
    }

    public void resizeLines(int count) {
        lineCount = Math.max(1, Math.min(16, count));
        while (materials.size() < lineCount) materials.add("create:andesite");
        while (primaryAtStart.size() < lineCount) primaryAtStart.add(primaryAtStart.size() >= lineCount / 2);
        while (materials.size() > lineCount) materials.remove(materials.size() - 1);
        while (primaryAtStart.size() > lineCount) primaryAtStart.remove(primaryAtStart.size() - 1);
    }

    public Block foundationBlock() {
        return foundation.isBlank() ? null : BuiltInRegistries.BLOCK.get(dev.sjimo.rrce.platform.GameApi.id(foundation));
    }

    public BlockState foundationState() {
        Block block = foundationBlock();
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        return block instanceof SlabBlock ? state.setValue(SlabBlock.TYPE, SlabType.DOUBLE) : state;
    }

    public Block wallBlock() {
        return wall.isBlank() ? null : BuiltInRegistries.BLOCK.get(dev.sjimo.rrce.platform.GameApi.id(wall));
    }

    /** Empty means skip construction; air is an explicit clearing material. */
    public static boolean isValidBlockMaterial(String id) {
        if (id.isBlank()) return true;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return false;
        BlockState state = BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        return state.isAir() || state.blocksMotion();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("lines", lineCount);
        tag.putInt("spacing", spacing);
        tag.putInt("margin", edgeMargin);
        tag.putInt("threshold", obstacleThreshold);
        tag.putInt("tunnelHeight", tunnelHeight);
        tag.putInt("tunnelSide", tunnelSideClearance);
        tag.putInt("wallThickness", wallThickness);
        tag.putInt("roofThickness", roofThickness);
        tag.putDouble("cutAngle", cutAngle);
        tag.putBoolean("replaceFoundation", replaceFoundation);
        tag.putBoolean("terrainWork", terrainWork);
        tag.putBoolean("allBlocksTerrain", allBlocksTerrain);
        tag.putString("foundation", foundation);
        tag.putString("wall", wall);
        ListTag lines = new ListTag();
        for (int i = 0; i < lineCount; i++) {
            CompoundTag line = new CompoundTag();
            line.putString("material", materials.get(i));
            line.putBoolean("primaryAtStart", primaryAtStart.get(i));
            lines.add(line);
        }
        tag.put("lineSettings", lines);
        return tag;
    }

    public static ConstructionConfig load(CompoundTag tag) {
        ConstructionConfig out = new ConstructionConfig();
        out.resizeLines(tag.contains("lines") ? tag.getInt("lines") : 2);
        out.spacing = bounded(tag.getInt("spacing"), 1, 32, 3);
        out.edgeMargin = bounded(tag.getInt("margin"), 0, 16, 2);
        out.obstacleThreshold = bounded(tag.getInt("threshold"), 1, 64, 8);
        out.tunnelHeight = bounded(tag.getInt("tunnelHeight"), 3, 32, 6);
        out.tunnelSideClearance = bounded(tag.getInt("tunnelSide"), 0, 16, 0);
        out.wallThickness = bounded(tag.getInt("wallThickness"), 1, 8, 1);
        out.roofThickness = bounded(tag.getInt("roofThickness"), 1, 8, 1);
        out.cutAngle = boundedAngle(tag.getDouble("cutAngle"));
        out.replaceFoundation = !tag.contains("replaceFoundation") || tag.getBoolean("replaceFoundation");
        out.terrainWork = !tag.contains("terrainWork") || tag.getBoolean("terrainWork");
        out.allBlocksTerrain = tag.getBoolean("allBlocksTerrain");
        if (tag.contains("foundation")) out.foundation = tag.getString("foundation");
        if (tag.contains("wall")) out.wall = tag.getString("wall");
        ListTag lines = tag.getList("lineSettings", 10);
        for (int i = 0; i < Math.min(lines.size(), out.lineCount); i++) {
            CompoundTag line = lines.getCompound(i);
            if (line.contains("material")) out.materials.set(i, line.getString("material"));
            out.primaryAtStart.set(i, line.getBoolean("primaryAtStart"));
        }
        return out;
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value >= min && value <= max ? value : fallback;
    }

    private static double boundedAngle(double value) {
        return Double.isFinite(value) && value >= 5 && value <= 85 ? value : Math.toDegrees(Math.atan(2));
    }
}
