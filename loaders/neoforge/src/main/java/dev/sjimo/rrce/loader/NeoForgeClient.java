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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgeClient implements ClientNetwork.Backend {
    private final Map<ResourceLocation, ClientNetwork.Receiver> receivers = new HashMap<>();
    public static void initialize(IEventBus bus) { bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> new NeoForgeClient().initialize())); }
    private void initialize() {
        ClientNetwork.install(this);
        NeoForgeEntrypoint.clientReceiver = (id, bytes) -> {
            ClientNetwork.Receiver receiver = receivers.get(id);
            Minecraft client = Minecraft.getInstance();
            if (receiver != null) {
                FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                try { receiver.receive(client, client.getConnection(), data, null); }
                finally { data.release(); }
            }
        };
        new RrceClient().onInitializeClient();
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::disconnect);
        NeoForge.EVENT_BUS.addListener(this::hud);
        NeoForge.EVENT_BUS.addListener(this::world);
    }
    private void tick(ClientTickEvent.Post event) { ClientEvents.ClientTickEvents.END_CLIENT_TICK.listeners.forEach(callback -> callback.accept(Minecraft.getInstance())); }
    private void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { ClientEvents.ClientPlayConnectionEvents.DISCONNECT.listeners.forEach(callback -> callback.disconnect(Minecraft.getInstance().getConnection(), Minecraft.getInstance())); }
    private void hud(RenderGuiEvent.Post event) { ClientEvents.HudRenderCallback.EVENT.listeners.forEach(callback -> callback.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false))); }
    private void world(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            ClientEvents.WorldRenderEvents.AFTER_ENTITIES.listeners.forEach(callback -> callback.accept(new ClientEvents.RenderContext(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera())));
    }
    @Override public void registerReceiver(ResourceLocation id, ClientNetwork.Receiver receiver) { receivers.put(id, receiver); }
    @Override public boolean canSend(ResourceLocation id) { return Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(NeoForgeEntrypoint.Message.TYPE); }
    @Override public void send(ResourceLocation id, FriendlyByteBuf data) { PacketDistributor.sendToServer(NeoForgeEntrypoint.message(id, data)); }
}
