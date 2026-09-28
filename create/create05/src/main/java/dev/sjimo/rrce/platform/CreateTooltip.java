package dev.sjimo.rrce.platform;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper.Palette;
import net.minecraft.world.item.Item;
public final class CreateTooltip {
    public static ItemDescription description(Item item) { return ItemDescription.create(item, Palette.STANDARD_CREATE); }
}
