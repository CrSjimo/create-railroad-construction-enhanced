package dev.sjimo.rrce.platform;

import java.util.ArrayList;
import java.util.List;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class Events {
    private Events() {}
    public static final class Event<T> {
        public final List<T> listeners = new ArrayList<>();
        public void register(T listener) { listeners.add(listener); }
    }
    @FunctionalInterface public interface UseBlock { InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit); }
    @FunctionalInterface public interface Join { void join(ServerGamePacketListenerImpl handler, Object sender, MinecraftServer server); }
    @FunctionalInterface public interface Disconnect { void disconnect(ServerGamePacketListenerImpl handler, MinecraftServer server); }
    @FunctionalInterface public interface WorldChange { void change(ServerPlayer player, ServerLevel origin, ServerLevel destination); }
    @FunctionalInterface public interface CommandRegistration { void register(CommandDispatcher<CommandSourceStack> dispatcher, Object registry, Object environment); }
    public static final class UseBlockCallback { public static final Event<UseBlock> EVENT = new Event<>(); }
    public static final class ServerPlayConnectionEvents {
        public static final Event<Join> JOIN = new Event<>();
        public static final Event<Disconnect> DISCONNECT = new Event<>();
    }
    public static final class ServerEntityWorldChangeEvents { public static final Event<WorldChange> AFTER_PLAYER_CHANGE_WORLD = new Event<>(); }
    public static final class ServerTickEvents { public static final Event<java.util.function.Consumer<MinecraftServer>> END_SERVER_TICK = new Event<>(); }
    public static final class ServerLifecycleEvents { public static final Event<java.util.function.Consumer<MinecraftServer>> SERVER_STARTING = new Event<>(); }
    public static final class CommandRegistrationCallback { public static final Event<CommandRegistration> EVENT = new Event<>(); }
    public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        for (UseBlock listener : UseBlockCallback.EVENT.listeners) {
            InteractionResult result = listener.use(player, level, hand, hit);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    }
}
