package dev.sjimo.rrce.platform;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.network.FriendlyByteBuf;

public final class Platform {
    public interface Backend {
        void registerItem(ResourceLocation id, Item item);
        void registerTab(ResourceLocation id, CreativeModeTab tab);
        CreativeModeTab.Builder creativeTabBuilder();
        boolean isModLoaded(String id);
        Path configDir();
        void registerReceiver(ResourceLocation id, Network.ServerReceiver receiver);
        void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf data);
    }
    private static Backend backend;
    private Platform() {}
    public static void install(Backend implementation) { backend = implementation; }
    public static Backend get() {
        if (backend == null) throw new IllegalStateException("RRCE platform has not been initialized");
        return backend;
    }
    public static void registerItem(ResourceLocation id, Item item) { get().registerItem(id, item); }
    public static void registerTab(ResourceLocation id, CreativeModeTab tab) { get().registerTab(id, tab); }
    public static CreativeModeTab.Builder creativeTabBuilder() { return get().creativeTabBuilder(); }
}
