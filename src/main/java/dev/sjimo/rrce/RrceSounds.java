package dev.sjimo.rrce;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Construction feedback heard by the player and nearby builders. */
public final class RrceSounds {
    private RrceSounds() {}

    public static void rotate(ServerPlayer player) { play(player, "create:wrench_rotate"); }
    public static void remove(ServerPlayer player) { play(player, "create:wrench_remove"); }
    public static void deny(ServerPlayer player) { play(player, "create:deny"); }
    public static void place(ServerPlayer player) { play(player, "minecraft:block.stone.place"); }
    public static void breakBlock(ServerPlayer player) { play(player, "minecraft:block.stone.break"); }

    private static void play(ServerPlayer player, String id) {
        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(id));
        player.serverLevel().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, .8f, 1f);
    }
}
