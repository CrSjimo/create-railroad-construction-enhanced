package dev.sjimo.rrce;

import dev.sjimo.rrce.client.RrceClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** One tool action per physical use-key press, regardless of held-use repeats. */
final class ToolPress {
    private ToolPress() {}

    static boolean claim(Player player, Level level) {
        return level.isClientSide ? RrceClient.claimToolPress()
            : SessionManager.get(player.getUUID()).claimToolPress();
    }
}
