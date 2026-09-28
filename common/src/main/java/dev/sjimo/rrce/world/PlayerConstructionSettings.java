package dev.sjimo.rrce.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.sjimo.rrce.ConstructionConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world construction settings. Route points and edit history remain session-only. */
public final class PlayerConstructionSettings extends dev.sjimo.rrce.platform.CompatSavedData {
    private static final String DATA_ID = "rrce_player_settings";
    private final Map<UUID, CompoundTag> settings = new HashMap<>();

    public static PlayerConstructionSettings forServer(MinecraftServer server) {
        return dev.sjimo.rrce.platform.GameApi.savedData(server,
            PlayerConstructionSettings::load, PlayerConstructionSettings::new, DATA_ID);
    }

    public ConstructionConfig get(UUID playerId) {
        CompoundTag tag = settings.get(playerId);
        return tag == null ? new ConstructionConfig() : ConstructionConfig.load(tag);
    }

    public void put(UUID playerId, ConstructionConfig config) {
        CompoundTag tag = config.save();
        if (!tag.equals(settings.put(playerId, tag))) setDirty();
    }

    static PlayerConstructionSettings load(CompoundTag root) {
        PlayerConstructionSettings data = new PlayerConstructionSettings();
        ListTag players = root.getList("players", 10);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("uuid") && entry.contains("config", 10))
                data.settings.put(entry.getUUID("uuid"), entry.getCompound("config").copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : settings.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", entry.getKey());
            player.put("config", entry.getValue().copy());
            players.add(player);
        }
        root.put("players", players);
        return root;
    }
}
