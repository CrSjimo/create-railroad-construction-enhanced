package dev.sjimo.rrce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class ConstructionConfigTest {
    @Test void foundationReplacementDefaultsOnButSavedOffRemainsOff() {
        assertTrue(new ConstructionConfig().replaceFoundation);
        assertTrue(ConstructionConfig.load(new CompoundTag()).replaceFoundation);

        ConstructionConfig explicit = new ConstructionConfig();
        explicit.replaceFoundation = false;
        assertFalse(ConstructionConfig.load(explicit.save()).replaceFoundation);
    }

    @Test void allBlocksTerrainDefaultsOffAndRoundTripsThroughThePlan() {
        assertFalse(new ConstructionConfig().allBlocksTerrain);
        assertFalse(ConstructionConfig.load(new CompoundTag()).allBlocksTerrain);
        ConstructionConfig enabled = new ConstructionConfig();
        enabled.allBlocksTerrain = true;
        assertTrue(ConstructionConfig.load(enabled.save()).allBlocksTerrain);
    }

    @Test void emptyTrackMaterialSurvivesSavingAndLineChanges() {
        ConstructionConfig config = new ConstructionConfig();
        config.materials.set(0, "");
        ConstructionConfig loaded = ConstructionConfig.load(config.save());
        assertEquals("", loaded.materials.get(0));
        assertEquals("create:andesite", loaded.materials.get(1));
        loaded.resizeLines(3);
        assertEquals("", loaded.materials.get(0));
        assertEquals("create:andesite", loaded.materials.get(2));
    }

    @Test void missingTrackMaterialInOlderSettingsKeepsDefault() {
        CompoundTag old = new CompoundTag();
        ListTag lines = new ListTag();
        lines.add(new CompoundTag());
        old.put("lineSettings", lines);
        assertEquals("create:andesite", ConstructionConfig.load(old).materials.get(0));
    }

    @Test void tunnelSideClearanceDefaultsToZeroButPreservesSavedValues() {
        assertEquals(0, new ConstructionConfig().tunnelSideClearance);
        assertEquals(0, ConstructionConfig.load(new CompoundTag()).tunnelSideClearance);
        ConstructionConfig custom = new ConstructionConfig();
        custom.tunnelSideClearance = 2;
        assertEquals(2, ConstructionConfig.load(custom.save()).tunnelSideClearance);
    }
}
