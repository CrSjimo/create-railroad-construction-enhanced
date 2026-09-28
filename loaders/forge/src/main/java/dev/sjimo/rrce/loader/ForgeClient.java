package dev.sjimo.rrce.loader;

import java.util.HashMap;
import java.util.Map;
import dev.sjimo.rrce.client.RrceClient;
import dev.sjimo.rrce.client.platform.ClientEvents;
import dev.sjimo.rrce.client.platform.ClientNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ForgeClient implements ClientNetwork.Backend {
    private final Map<ResourceLocation, ClientNetwork.Receiver> receivers = new HashMap<>();
    public static void initialize(IEventBus bus) { bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> new ForgeClient().initialize())); }
    private void initialize() {
        ClientNetwork.install(this);
        ForgeEntrypoint.clientReceiver = (id, bytes) -> {
            ClientNetwork.Receiver receiver = receivers.get(id);
            Minecraft client = Minecraft.getInstance();
            if (receiver != null) {
                FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                try { receiver.receive(client, client.getConnection(), data, null); }
                finally { data.release(); }
            }
        };
        new RrceClient().onInitializeClient();
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::disconnect);
        MinecraftForge.EVENT_BUS.addListener(this::hud);
        MinecraftForge.EVENT_BUS.addListener(this::world);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientEvents.ClientTickEvents.END_CLIENT_TICK.listeners.forEach(callback -> callback.accept(Minecraft.getInstance()));
    }
    private void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { ClientEvents.ClientPlayConnectionEvents.DISCONNECT.listeners.forEach(callback -> callback.disconnect(Minecraft.getInstance().getConnection(), Minecraft.getInstance())); }
    private void hud(RenderGuiEvent.Post event) { ClientEvents.HudRenderCallback.EVENT.listeners.forEach(callback -> callback.render(event.getGuiGraphics(), event.getPartialTick())); }
    private void world(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            ClientEvents.WorldRenderEvents.AFTER_ENTITIES.listeners.forEach(callback -> callback.accept(new ClientEvents.RenderContext(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera())));
    }
    @Override public void registerReceiver(ResourceLocation id, ClientNetwork.Receiver receiver) { receivers.put(id, receiver); }
    @Override public boolean canSend(ResourceLocation id) { return Minecraft.getInstance().getConnection() != null && ForgeEntrypoint.CHANNEL.isRemotePresent(Minecraft.getInstance().getConnection().getConnection()); }
    @Override public void send(ResourceLocation id, FriendlyByteBuf data) { ForgeEntrypoint.CHANNEL.sendToServer(ForgeEntrypoint.message(id, data)); }
}
