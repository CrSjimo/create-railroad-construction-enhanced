package dev.sjimo.rrce.platform;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class CompatItem extends Item {
    public CompatItem(Properties properties) { super(properties); }
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {}
    @Override public final void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        appendHoverText(stack, context.level(), lines, flag);
    }
}
