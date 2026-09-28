package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TerrainRulesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        dev.sjimo.rrce.TestBootstrap.bootStrap();
    }

    @Test void defaultsKeepNaturalGroundAndProtectExistingSpecialBlocks() {
        TerrainRules.RuleSet rules = TerrainRules.current();
        assertTrue(rules.isTerrain(Blocks.GRAVEL.defaultBlockState(), false));
        assertFalse(rules.isTerrain(Blocks.OAK_LOG.defaultBlockState(), false));
        assertFalse(rules.isTerrain(Blocks.BEDROCK.defaultBlockState(), false));
        assertFalse(rules.isRemovable(Blocks.BEDROCK.defaultBlockState(), false));
    }

    @Test void exclusionsAndProtectionOverrideTerrainUntilThePlanUsesAllBlocksMode() {
        TerrainRules.RuleSet rules = TerrainRules.fromJson(JsonParser.parseString("""
            {
              "terrainBlocks": ["minecraft:gravel", "minecraft:oak_log"],
              "terrainTags": [],
              "nonTerrainBlocks": ["minecraft:gravel"],
              "nonTerrainTags": [],
              "protectedBlocks": ["minecraft:oak_log"],
              "protectedTags": [],
              "protectTrackBlocks": false
            }
            """).getAsJsonObject());
        assertFalse(rules.isTerrain(Blocks.GRAVEL.defaultBlockState(), false));
        assertFalse(rules.isTerrain(Blocks.OAK_LOG.defaultBlockState(), false));
        assertFalse(rules.isRemovable(Blocks.OAK_LOG.defaultBlockState(), false));
        assertTrue(rules.isTerrain(Blocks.GRAVEL.defaultBlockState(), true));
        assertTrue(rules.isTerrain(Blocks.OAK_LOG.defaultBlockState(), true));
        assertTrue(rules.isRemovable(Blocks.OAK_LOG.defaultBlockState(), true));
        assertFalse(rules.isTerrain(Blocks.AIR.defaultBlockState(), true));
    }

    @Test void unknownBlockIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TerrainRules.fromJson(JsonParser.parseString("""
            {
              "terrainBlocks": ["rrce:no_such_block"],
              "terrainTags": [],
              "nonTerrainBlocks": [],
              "nonTerrainTags": [],
              "protectedBlocks": [],
              "protectedTags": []
            }
            """).getAsJsonObject()));
    }
}
