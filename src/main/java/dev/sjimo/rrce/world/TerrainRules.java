package dev.sjimo.rrce.world;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.content.trains.track.TrackBlock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned rules for obstacle classification and protected excavation blocks. */
public final class TerrainRules {
    public static final String FILE_NAME = "rrce-terrain.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile RuleSet current = defaults();

    private TerrainRules() {}

    public record RuleSet(Set<ResourceLocation> terrainBlocks, Set<TagKey<Block>> terrainTags,
                          Set<ResourceLocation> nonTerrainBlocks, Set<TagKey<Block>> nonTerrainTags,
                          Set<ResourceLocation> protectedBlocks, Set<TagKey<Block>> protectedTags,
                          boolean protectTrackBlocks) {
        public boolean isTerrain(BlockState state, boolean allBlocksTerrain) {
            if (state.isAir()) return false;
            if (allBlocksTerrain) return true;
            if (isProtected(state) || matches(state, nonTerrainBlocks, nonTerrainTags)) return false;
            return matches(state, terrainBlocks, terrainTags);
        }

        public boolean isRemovable(BlockState state, boolean allBlocksTerrain) {
            return allBlocksTerrain || !isProtected(state);
        }

        private boolean isProtected(BlockState state) {
            return protectTrackBlocks && state.getBlock() instanceof TrackBlock
                || matches(state, protectedBlocks, protectedTags);
        }
    }

    public static RuleSet current() { return current; }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME); }

    public static void reload() throws IOException {
        Path file = path();
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            try {
                Files.writeString(file, GSON.toJson(toJson(defaults())), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another server startup created the file before this read.
            }
        }
        RuleSet loaded;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded = fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        }
        current = loaded;
    }

    public static RuleSet fromJson(JsonObject json) {
        return new RuleSet(blocks(json, "terrainBlocks"), tags(json, "terrainTags"),
            blocks(json, "nonTerrainBlocks"), tags(json, "nonTerrainTags"),
            blocks(json, "protectedBlocks"), tags(json, "protectedTags"),
            json.has("protectTrackBlocks") ? json.get("protectTrackBlocks").getAsBoolean() : true);
    }

    private static boolean matches(BlockState state, Set<ResourceLocation> blocks, Set<TagKey<Block>> tags) {
        if (blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return true;
        for (TagKey<Block> tag : tags) if (state.is(tag)) return true;
        return false;
    }

    private static Set<ResourceLocation> blocks(JsonObject json, String field) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement entry : requiredArray(json, field)) {
            ResourceLocation id = parseId(entry.getAsString(), field);
            if (!BuiltInRegistries.BLOCK.containsKey(id))
                throw new IllegalArgumentException("Unknown block in " + field + ": " + id);
            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static Set<TagKey<Block>> tags(JsonObject json, String field) {
        Set<TagKey<Block>> result = new LinkedHashSet<>();
        for (JsonElement entry : requiredArray(json, field))
            result.add(TagKey.create(Registries.BLOCK, parseId(entry.getAsString(), field)));
        return Set.copyOf(result);
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray())
            throw new IllegalArgumentException("Missing array in " + FILE_NAME + ": " + field);
        return json.getAsJsonArray(field);
    }

    private static ResourceLocation parseId(String raw, String field) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid ID in " + field + ": " + raw);
        return id;
    }

    private static RuleSet defaults() {
        return new RuleSet(ids("minecraft:gravel", "minecraft:clay", "minecraft:terracotta",
                "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:netherrack",
                "minecraft:end_stone", "minecraft:sandstone", "minecraft:red_sandstone",
                "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:blackstone", "minecraft:basalt", "minecraft:magma_block"),
            blockTags("minecraft:base_stone_overworld", "minecraft:dirt", "minecraft:sand",
                "minecraft:coal_ores", "minecraft:iron_ores", "minecraft:copper_ores",
                "minecraft:gold_ores", "minecraft:diamond_ores", "minecraft:emerald_ores",
                "minecraft:lapis_ores", "minecraft:redstone_ores"),
            Set.of(), Set.of(),
            ids("minecraft:bedrock", "minecraft:barrier", "minecraft:end_portal_frame",
                "minecraft:command_block", "minecraft:chain_command_block",
                "minecraft:repeating_command_block"), Set.of(), true);
    }

    private static Set<ResourceLocation> ids(String... raw) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        Arrays.stream(raw).map(ResourceLocation::new).forEach(result::add);
        return Set.copyOf(result);
    }

    private static Set<TagKey<Block>> blockTags(String... raw) {
        Set<TagKey<Block>> result = new LinkedHashSet<>();
        Arrays.stream(raw).map(ResourceLocation::new)
            .map(id -> TagKey.create(Registries.BLOCK, id)).forEach(result::add);
        return Set.copyOf(result);
    }

    private static JsonObject toJson(RuleSet rules) {
        JsonObject json = new JsonObject();
        array(json, "terrainBlocks", rules.terrainBlocks.stream().map(Object::toString).sorted().toList());
        array(json, "terrainTags", rules.terrainTags.stream().map(tag -> tag.location().toString()).sorted().toList());
        array(json, "nonTerrainBlocks", rules.nonTerrainBlocks.stream().map(Object::toString).sorted().toList());
        array(json, "nonTerrainTags", rules.nonTerrainTags.stream().map(tag -> tag.location().toString()).sorted().toList());
        array(json, "protectedBlocks", rules.protectedBlocks.stream().map(Object::toString).sorted().toList());
        array(json, "protectedTags", rules.protectedTags.stream().map(tag -> tag.location().toString()).sorted().toList());
        json.addProperty("protectTrackBlocks", rules.protectTrackBlocks);
        return json;
    }

    private static void array(JsonObject json, String field, java.util.List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(field, array);
    }
}
