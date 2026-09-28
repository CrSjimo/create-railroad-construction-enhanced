package dev.sjimo.rrce.loader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import dev.sjimo.rrce.RrceMod;
import dev.sjimo.rrce.platform.Events;
import dev.sjimo.rrce.platform.Network;
import dev.sjimo.rrce.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;

@Mod(RrceMod.ID)
public final class ForgeEntrypoint implements Platform.Backend {
    private final Map<ResourceLocation, Item> items = new LinkedHashMap<>();
    private final Map<ResourceLocation, CreativeModeTab> tabs = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Network.ServerReceiver> receivers = new HashMap<>();
    public static BiConsumer<ResourceLocation, byte[]> clientReceiver;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(RrceMod.ID, "main"), () -> "1", "1"::equals, "1"::equals);
    public record Message(ResourceLocation id, byte[] bytes) {
        static void encode(Message message, FriendlyByteBuf buffer) { buffer.writeResourceLocation(message.id); buffer.writeByteArray(message.bytes); }
        static Message decode(FriendlyByteBuf buffer) { return new Message(buffer.readResourceLocation(), buffer.readByteArray(1_048_576)); }
    }
    public ForgeEntrypoint() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        Platform.install(this);
        bus.addListener(this::register);
        CHANNEL.registerMessage(0, Message.class, Message::encode, Message::decode, (message, contextSupplier) -> {
            var context = contextSupplier.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                ServerPlayer player = context.getSender();
                Network.ServerReceiver receiver = receivers.get(message.id);
                if (player != null && receiver != null) {
                    FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(message.bytes));
                    try { receiver.receive(player.server, player, player.connection, data, null); }
                    finally { data.release(); }
                }
            } else if (clientReceiver != null) clientReceiver.accept(message.id, message.bytes);
            context.setPacketHandled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(this::useBlock);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::disconnect);
        MinecraftForge.EVENT_BUS.addListener(this::changeWorld);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::starting);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.initialize(bus);
    }
    private void register(RegisterEvent event) {
        // Intrusive item holders may only be created while the item registry is open.
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            new RrceMod().onInitialize();
            items.forEach((id, item) -> event.register(Registries.ITEM, id, () -> item));
        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tabs.forEach((id, tab) -> event.register(Registries.CREATIVE_MODE_TAB, id, () -> tab));
        }
    }
    private void useBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = Events.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result != InteractionResult.PASS) { event.setCanceled(true); event.setCancellationResult(result); }
    }
    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Events.ServerPlayConnectionEvents.JOIN.listeners.forEach(callback -> callback.join(player.connection, null, player.server));
    }
    private void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Events.ServerPlayConnectionEvents.DISCONNECT.listeners.forEach(callback -> callback.disconnect(player.connection, player.server));
    }
    private void changeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Events.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.listeners.forEach(callback -> callback.change(player, player.server.getLevel(event.getFrom()), player.server.getLevel(event.getTo())));
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) Events.ServerTickEvents.END_SERVER_TICK.listeners.forEach(callback -> callback.accept(event.getServer()));
    }
    private void starting(ServerStartingEvent event) { Events.ServerLifecycleEvents.SERVER_STARTING.listeners.forEach(callback -> callback.accept(event.getServer())); }
    private void commands(RegisterCommandsEvent event) { Events.CommandRegistrationCallback.EVENT.listeners.forEach(callback -> callback.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection())); }
    @Override public void registerItem(ResourceLocation id, Item item) { items.put(id, item); }
    @Override public void registerTab(ResourceLocation id, CreativeModeTab tab) { tabs.put(id, tab); }
    @Override public CreativeModeTab.Builder creativeTabBuilder() { return CreativeModeTab.builder(); }
    @Override public boolean isModLoaded(String id) { return dev.sjimo.rrce.platform.PlatformLoader.getInstance().isModLoaded(id); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public void registerReceiver(ResourceLocation id, Network.ServerReceiver receiver) { receivers.put(id, receiver); }
    @Override public void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message(id, data));
    }
    public static Message message(ResourceLocation id, FriendlyByteBuf data) {
        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), bytes);
        data.release();
        return new Message(id, bytes);
    }
}
