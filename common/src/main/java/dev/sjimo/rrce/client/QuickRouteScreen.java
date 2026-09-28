package dev.sjimo.rrce.client;

import dev.sjimo.rrce.QuickRouteGeometry;
import dev.sjimo.rrce.QuickRouteTool;
import dev.sjimo.rrce.RrceMod;
import dev.sjimo.rrce.UserFacingException;
import dev.sjimo.rrce.client.platform.ClientNetwork.ClientPlayNetworking;
import dev.sjimo.rrce.platform.Network.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Edits the selected quick route item's own settings. */
public final class QuickRouteScreen extends dev.sjimo.rrce.client.platform.CompatScreen {
    private final QuickRouteGeometry.Kind kind;
    private EditBox first;
    private EditBox second;
    private Component error = Component.empty();
    private int left, top;

    public QuickRouteScreen(QuickRouteGeometry.Kind kind) {
        super(Component.translatable("screen.rrce.quick." + kind.id));
        this.kind = kind;
    }

    @Override
    protected void init() {
        left = (width - 252) / 2;
        top = (height - 152) / 2;
        QuickRouteGeometry.Settings settings;
        try {
            settings = minecraft.player != null
                ? QuickRouteTool.settings(minecraft.player.getMainHandItem(), kind)
                : QuickRouteGeometry.defaults(kind);
        } catch (RuntimeException invalid) {
            settings = QuickRouteGeometry.defaults(kind);
        }
        first = new EditBox(font, left + 14, top + 52, 224, 20,
            Component.translatable("screen.rrce.quick.first." + kind.id));
        first.setValue(number(settings.first()));
        addRenderableWidget(first);
        second = new EditBox(font, left + 14, top + 99, 224, 20,
            Component.translatable("screen.rrce.quick.second." + kind.id));
        second.setValue(number(settings.second()));
        addRenderableWidget(second);
        addRenderableWidget(Button.builder(Component.translatable("screen.rrce.save"), button -> save())
            .bounds(left + 14, top + 127, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.rrce.close"), button -> onClose())
            .bounds(left + 130, top + 127, 108, 20).build());
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private void save() {
        try {
            QuickRouteGeometry.Settings settings = new QuickRouteGeometry.Settings(
                Double.parseDouble(first.getValue().trim()), Double.parseDouble(second.getValue().trim()));
            QuickRouteGeometry.validate(kind, settings);
            if (minecraft.player == null || !(minecraft.player.getMainHandItem().getItem() instanceof QuickRouteTool tool)
                || tool.kind != kind) throw new UserFacingException("error.rrce.quick_tool_required");
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(kind.id, 32);
            buf.writeDouble(settings.first());
            buf.writeDouble(settings.second());
            ClientPlayNetworking.send(RrceMod.QUICK_CONFIG_PACKET, buf);
            onClose();
        } catch (NumberFormatException invalid) {
            error = Component.translatable("error.rrce.quick_number");
        } catch (RuntimeException invalid) {
            error = UserFacingException.display(invalid);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(left, top, left + 252, top + 153, 0xE0151C1E);
        graphics.fill(left, top, left + 252, top + 2, 0xFFB99B5D);
        graphics.drawCenteredString(font, title, width / 2, top + 9, 0xFFE7D29B);
        graphics.drawString(font, Component.translatable("screen.rrce.quick.first." + kind.id),
            left + 14, top + 39, 0xFFDDDDDD);
        graphics.drawString(font, Component.translatable("screen.rrce.quick.second." + kind.id),
            left + 14, top + 86, 0xFFDDDDDD);
        if (!error.getString().isEmpty())
            graphics.drawString(font, font.plainSubstrByWidth(error.getString(), 238),
                left + 7, top + 26, 0xFFFF9D87);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
