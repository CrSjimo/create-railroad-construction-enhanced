package dev.sjimo.rrce.world;

import dev.sjimo.rrce.RrceMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Reads the installed addon's placement policy without making it a required dependency. */
public final class CreateUnlimitedCompat {
    private static boolean warned;

    private CreateUnlimitedCompat() {}

    public static boolean relaxPlacementChecks(ServerPlayer player) {
        if (!FabricLoader.getInstance().isModLoaded("createunlimited")) return false;
        try {
            Class<?> configs = Class.forName("fabric.dev.rdh.createunlimited.config.CUConfigs");
            Object server = configs.getField("server").get(null);
            Object setting = server.getClass().getField("placementChecks").get(server);
            Object value = setting.getClass().getMethod("get").invoke(setting);
            Object enabled = value.getClass().getMethod("isEnabledFor", Player.class).invoke(value, player);
            return Boolean.FALSE.equals(enabled);
        } catch (ReflectiveOperationException | LinkageError error) {
            if (!warned) {
                warned = true;
                RrceMod.LOGGER.warn("Unable to read Create Unlimited placementChecks; using Create placement limits", error);
            }
            return false;
        }
    }
}
