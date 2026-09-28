package dev.sjimo.rrce.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class Network {
    @FunctionalInterface public interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buffer, Object sender);
    }
    public static final class PacketByteBufs {
        public static FriendlyByteBuf create() { return new FriendlyByteBuf(Unpooled.buffer()); }
    }
    public static final class ServerPlayNetworking {
        public static void registerGlobalReceiver(ResourceLocation id, ServerReceiver receiver) { Platform.get().registerReceiver(id, receiver); }
        public static void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) { Platform.get().send(player, id, buffer); }
    }
}
