package dev.sjimo.rrce.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class PlayerConstructionSettingsTest {
    @Test void savesEachPlayersConstructionConfigInTheWorld() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlayerConstructionSettings original = new PlayerConstructionSettings();
        ConstructionConfig firstConfig = new ConstructionConfig();
        firstConfig.spacing = 5;
        firstConfig.resizeLines(3);
        ConstructionConfig secondConfig = new ConstructionConfig();
        secondConfig.foundation = "minecraft:stone";

        original.put(first, firstConfig);
        original.put(second, secondConfig);
        PlayerConstructionSettings loaded = PlayerConstructionSettings.load(original.save(new CompoundTag()));

        assertEquals(5, loaded.get(first).spacing);
        assertEquals(3, loaded.get(first).lineCount);
        assertEquals("minecraft:stone", loaded.get(second).foundation);
        assertEquals(3, loaded.get(second).spacing);
        assertEquals(3, loaded.get(UUID.randomUUID()).spacing);
    }
}
