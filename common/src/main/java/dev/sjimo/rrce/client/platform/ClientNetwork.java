package dev.sjimo.rrce.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ClientNetwork {
    @FunctionalInterface public interface Receiver { void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buffer, Object sender); }
    public interface Backend {
        void registerReceiver(ResourceLocation id, Receiver receiver);
        boolean canSend(ResourceLocation id);
        void send(ResourceLocation id, FriendlyByteBuf data);
    }
    private static Backend backend;
    public static void install(Backend implementation) { backend = implementation; }
    public static final class ClientPlayNetworking {
        public static void registerGlobalReceiver(ResourceLocation id, Receiver receiver) { backend.registerReceiver(id, receiver); }
        public static boolean canSend(ResourceLocation id) { return backend.canSend(id); }
        public static void send(ResourceLocation id, FriendlyByteBuf data) { backend.send(id, data); }
    }
}
