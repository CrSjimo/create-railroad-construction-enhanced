package dev.sjimo.rrce.client.platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) { super(title); }
    public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) { super.renderBackground(graphics); }
}
