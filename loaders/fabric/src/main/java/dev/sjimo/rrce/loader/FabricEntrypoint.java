package dev.sjimo.rrce.loader;

import dev.sjimo.rrce.RrceMod;
import dev.sjimo.rrce.platform.Events;
import dev.sjimo.rrce.platform.Network;
import dev.sjimo.rrce.platform.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import java.nio.file.Path;

public final class FabricEntrypoint implements ModInitializer, Platform.Backend {
    @Override public void onInitialize() {
        Platform.install(this);
        new RrceMod().onInitialize();
        UseBlockCallback.EVENT.register(Events::useBlock);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            Events.ServerPlayConnectionEvents.JOIN.listeners.forEach(callback -> callback.join(handler, sender, server)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            Events.ServerPlayConnectionEvents.DISCONNECT.listeners.forEach(callback -> callback.disconnect(handler, server)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            Events.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.listeners.forEach(callback -> callback.change(player, origin, destination)));
        ServerTickEvents.END_SERVER_TICK.register(server -> Events.ServerTickEvents.END_SERVER_TICK.listeners.forEach(callback -> callback.accept(server)));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Events.ServerLifecycleEvents.SERVER_STARTING.listeners.forEach(callback -> callback.accept(server)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            Events.CommandRegistrationCallback.EVENT.listeners.forEach(callback -> callback.register(dispatcher, registry, environment)));
    }
    @Override public void registerItem(ResourceLocation id, Item item) { Registry.register(BuiltInRegistries.ITEM, id, item); }
    @Override public void registerTab(ResourceLocation id, CreativeModeTab tab) { Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab); }
    @Override public CreativeModeTab.Builder creativeTabBuilder() { return FabricItemGroup.builder(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public void registerReceiver(ResourceLocation id, Network.ServerReceiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(id, receiver::receive);
    }
    @Override public void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf data) { ServerPlayNetworking.send(player, id, data); }
}
