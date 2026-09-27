package dev.sjimo.rrce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class PonderSchematicTest {
    @Test
    void stageHasUsableBoundsAndAnEmptyBasePlate() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/assets/rrce/ponder/railway_stage.nbt")) {
            assertNotNull(stream);
            CompoundTag schematic = NbtIo.readCompressed(stream);
            assertEquals(15, schematic.getList("size", 3).getInt(0));
            assertEquals(12, schematic.getList("size", 3).getInt(1));
            assertEquals(15, schematic.getList("size", 3).getInt(2));
            assertEquals(226, schematic.getList("blocks", 10).size());
            assertEquals("minecraft:gray_concrete", schematic.getList("palette", 10)
                .getCompound(0).getString("Name"));
            CompoundTag boundsMarker = schematic.getList("blocks", 10).getCompound(225);
            assertEquals(14, boundsMarker.getList("pos", 3).getInt(0));
            assertEquals(11, boundsMarker.getList("pos", 3).getInt(1));
            assertEquals(14, boundsMarker.getList("pos", 3).getInt(2));
        }
    }
}
