package dev.sjimo.rrce.loader;

import dev.sjimo.rrce.client.RrceClient;
import dev.sjimo.rrce.client.platform.ClientEvents;
import dev.sjimo.rrce.client.platform.ClientNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class FabricClientEntrypoint implements ClientModInitializer, ClientNetwork.Backend {
    @Override public void onInitializeClient() {
        ClientNetwork.install(this);
        new RrceClient().onInitializeClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEvents.ClientTickEvents.END_CLIENT_TICK.listeners.forEach(callback -> callback.accept(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvents.ClientPlayConnectionEvents.DISCONNECT.listeners.forEach(callback -> callback.disconnect(handler, client)));
        HudRenderCallback.EVENT.register((graphics, tick) -> ClientEvents.HudRenderCallback.EVENT.listeners.forEach(callback -> callback.render(graphics, tick)));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> ClientEvents.WorldRenderEvents.AFTER_ENTITIES.listeners.forEach(callback ->
            callback.accept(new ClientEvents.RenderContext(context.matrixStack(), context.consumers(), context.camera()))));
    }
    @Override public void registerReceiver(ResourceLocation id, ClientNetwork.Receiver receiver) { ClientPlayNetworking.registerGlobalReceiver(id, receiver::receive); }
    @Override public boolean canSend(ResourceLocation id) { return ClientPlayNetworking.canSend(id); }
    @Override public void send(ResourceLocation id, FriendlyByteBuf data) { ClientPlayNetworking.send(id, data); }
}
