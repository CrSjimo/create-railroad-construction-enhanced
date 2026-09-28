package dev.sjimo.rrce.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sjimo.rrce.platform.Events.Event;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;

public final class ClientEvents {
    public record RenderContext(PoseStack matrixStack, MultiBufferSource consumers, Camera camera) {}
    @FunctionalInterface public interface Hud { void render(GuiGraphics graphics, float tickDelta); }
    @FunctionalInterface public interface Disconnect { void disconnect(ClientPacketListener handler, Minecraft client); }
    public static final class ClientTickEvents { public static final Event<java.util.function.Consumer<Minecraft>> END_CLIENT_TICK = new Event<>(); }
    public static final class HudRenderCallback { public static final Event<Hud> EVENT = new Event<>(); }
    public static final class WorldRenderEvents { public static final Event<java.util.function.Consumer<RenderContext>> AFTER_ENTITIES = new Event<>(); }
    public static final class ClientPlayConnectionEvents { public static final Event<Disconnect> DISCONNECT = new Event<>(); }
}
