package dev.sjimo.rrce;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.foundation.item.ItemDescription;
import dev.sjimo.rrce.platform.CreateTooltip;
import dev.sjimo.rrce.world.CreateUnlimitedCompat;
import dev.sjimo.rrce.world.RouteGeometry;
import dev.sjimo.rrce.world.SurveyPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class QuickRouteTool extends dev.sjimo.rrce.platform.CompatItem {
    private static final String SETTINGS_KEY = "RRCEQuickRoute";
    public final QuickRouteGeometry.Kind kind;

    public QuickRouteTool(QuickRouteGeometry.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public static QuickRouteGeometry.Settings settings(ItemStack stack, QuickRouteGeometry.Kind kind) {
        CompoundTag tag = dev.sjimo.rrce.platform.GameApi.itemTag(stack, SETTINGS_KEY);
        if (tag == null) return QuickRouteGeometry.defaults(kind);
        QuickRouteGeometry.Settings result = new QuickRouteGeometry.Settings(
            tag.contains("first", 6) ? tag.getDouble("first") : kind.defaultFirst,
            tag.contains("second", 6) ? tag.getDouble("second") : kind.defaultSecond);
        QuickRouteGeometry.validate(kind, result);
        return result;
    }

    public static void save(ItemStack stack, QuickRouteGeometry.Kind kind,
                            QuickRouteGeometry.Settings settings) {
        QuickRouteGeometry.validate(kind, settings);
        if (!(stack.getItem() instanceof QuickRouteTool tool) || tool.kind != kind)
            throw new UserFacingException("error.rrce.quick_tool_required");
        CompoundTag tag = dev.sjimo.rrce.platform.GameApi.editableItemTag(stack, SETTINGS_KEY);
        tag.putDouble("first", settings.first());
        tag.putDouble("second", settings.second());
        dev.sjimo.rrce.platform.GameApi.storeItemTag(stack, SETTINGS_KEY, tag);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return useAt(context.getPlayer(), context.getLevel(), context.getHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(useAt(player, level, hand), player.getItemInHand(hand));
    }

    public InteractionResult useAt(Player usingPlayer, Level level, InteractionHand hand) {
        if (usingPlayer == null || usingPlayer.isSpectator() || hand != InteractionHand.MAIN_HAND)
            return InteractionResult.FAIL;
        if (!ToolPress.claim(usingPlayer, level)) return InteractionResult.CONSUME;
        if (usingPlayer.isShiftKeyDown()) {
            if (level.isClientSide) dev.sjimo.rrce.client.RrceClient.openQuickScreen(kind);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer player = (ServerPlayer) usingPlayer;
        try {
            PlayerSession session = SessionManager.get(player.getUUID());
            session.select(session.selectedPoint);
            SurveyPoint start = session.points.get(session.selectedPoint);
            SurveyPoint end = QuickRouteGeometry.endpoint(start, kind,
                settings(player.getMainHandItem(), kind));
            RrceMod.validatePointPosition(player, end.pos());
            List<SurveyPoint> old = new ArrayList<>(session.points);
            int previous = session.selectedPoint;
            try {
                session.addPoint(end.pos(), end.heading());
                RouteGeometry.buildLayout(session.points, session.config,
                    CreateUnlimitedCompat.relaxPlacementChecks(player));
            } catch (RuntimeException invalid) {
                session.points.clear();
                session.points.addAll(old);
                session.selectedPoint = previous;
                throw invalid;
            }
            player.sendSystemMessage(Component.translatable("message.rrce.quick_point_added",
                session.selectedPoint + 1, end.pos().toShortString()));
            RrceMod.syncPointEdit(player, false);
        } catch (RuntimeException error) {
            RrceSounds.deny(player);
            if (!(error instanceof UserFacingException)) RrceMod.LOGGER.warn("Quick route action failed", error);
            player.sendSystemMessage(Component.translatable("message.rrce.operation_failed",
                UserFacingException.display(error)));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        ItemDescription description = CreateTooltip.description(this);
        if (description != null) lines.addAll(description.getCurrentLines());
    }
}
