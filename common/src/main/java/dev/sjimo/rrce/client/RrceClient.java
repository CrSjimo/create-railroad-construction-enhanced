package dev.sjimo.rrce.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackRenderer;
import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.RrceMod;
import dev.sjimo.rrce.world.SurveyPoint;
import dev.sjimo.rrce.world.TrackHeading;
import dev.sjimo.rrce.client.platform.AlphaVertexConsumer;
import dev.sjimo.rrce.client.platform.ClientEvents.ClientPlayConnectionEvents;
import dev.sjimo.rrce.client.platform.ClientNetwork.ClientPlayNetworking;
import dev.sjimo.rrce.client.platform.ClientEvents.ClientTickEvents;
import dev.sjimo.rrce.client.platform.ClientEvents.HudRenderCallback;
import dev.sjimo.rrce.client.platform.ClientEvents.WorldRenderEvents;
import dev.sjimo.rrce.platform.Network.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RrceClient {
    public static ConstructionConfig config = new ConstructionConfig();
    public static final List<SurveyPoint> points = new ArrayList<>();
    public static int selected = -1;
    public static boolean previewEnabled = true;
    public static boolean fullPreview;
    public static PreviewSnapshot preview = PreviewSnapshot.empty();
    private static final List<BezierConnection> curvePreviews = new ArrayList<>();
    private static volatile Set<Long> hiddenExistingBlocks = Set.of();
    private static boolean toolPressHeld;

    public record PreviewSnapshot(int revision, long[] clear, long[] blockPositions, int[] blockStates,
                                  int blocks, int curves, Component error) {
        static PreviewSnapshot empty() { return new PreviewSnapshot(0, new long[0], new long[0],
            new int[0], 0, 0, null); }
        static PreviewSnapshot header(CompoundTag tag) { return new PreviewSnapshot(tag.getInt("revision"),
            new long[0], new long[0], new int[0],
            tag.getInt("blocks"), tag.getInt("curves"), tag.contains("error")
                ? dev.sjimo.rrce.platform.GameApi.fromJson(tag.getString("error")) : null); }
        PreviewSnapshot append(CompoundTag tag) {
            String kind = tag.getString("kind");
            long[] part = tag.getLongArray("cells");
            return switch (kind) {
                case "clear" -> new PreviewSnapshot(revision, join(clear, part),
                    blockPositions, blockStates, blocks, curves, error);
                case "blocks" -> new PreviewSnapshot(revision, clear,
                    join(blockPositions, part), join(blockStates, tag.getIntArray("states")), blocks, curves, error);
                default -> this;
            };
        }
        private static long[] join(long[] old, long[] part) {
            long[] result = Arrays.copyOf(old, old.length + part.length);
            System.arraycopy(part, 0, result, old.length, part.length);
            return result;
        }
        private static int[] join(int[] old, int[] part) {
            int[] result = Arrays.copyOf(old, old.length + part.length);
            System.arraycopy(part, 0, result, old.length, part.length);
            return result;
        }
    }

    public void onInitializeClient() {
        RrcePonder.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toolPressHeld && !client.options.keyUse.isDown()) {
                toolPressHeld = false;
                if (ClientPlayNetworking.canSend(RrceMod.TOOL_RELEASE_PACKET))
                    ClientPlayNetworking.send(RrceMod.TOOL_RELEASE_PACKET, PacketByteBufs.create());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(RrceMod.CONFIG_SYNC, (client, handler, buf, sender) -> {
            CompoundTag state = buf.readNbt();
            client.execute(() -> {
                if (state == null) return;
                config = ConstructionConfig.load(state.getCompound("config"));
                points.clear();
                long[] positions = state.getLongArray("points");
                int[] headings = state.getIntArray("headings");
                for (int i = 0; i < positions.length; i++)
                    points.add(new SurveyPoint(BlockPos.of(positions[i]),
                        TrackHeading.byIndex(i < headings.length ? headings[i] : 0)));
                selected = state.getInt("selected");
                previewEnabled = state.getBoolean("preview");
                fullPreview = state.getBoolean("fullPreview");
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RrceMod.PREVIEW_SYNC, (client, handler, buf, sender) -> {
            CompoundTag state = buf.readNbt();
            client.execute(() -> {
                if (state == null) return;
                if (state.getString("kind").equals("header")) {
                    preview = PreviewSnapshot.header(state);
                    curvePreviews.clear();
                } else if (state.getInt("revision") == preview.revision()) {
                    if (state.getString("kind").equals("curve"))
                        curvePreviews.add(new BezierConnection(state.getCompound("connection"), BlockPos.ZERO));
                    else preview = preview.append(state);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            toolPressHeld = false;
            points.clear(); selected = -1; preview = PreviewSnapshot.empty(); curvePreviews.clear();
            hiddenExistingBlocks = Set.of();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (context.matrixStack() == null || context.consumers() == null) return;
            PoseStack pose = context.matrixStack();
            Vec3 camera = context.camera().getPosition();
            boolean holding = holdingConstructionItem(client);
            List<Integer> ghostBlocks = holding && previewEnabled && fullPreview
                ? visibleGhostBlocks(client, camera) : List.of();
            updateHiddenExistingBlocks(client, camera, ghostBlocks, holding && previewEnabled && fullPreview);
            if (!holding) return;
            // Match vanilla's block outline phase: the consumer is flushed with this view pose.
            if (previewEnabled && fullPreview) {
                VertexConsumer translucent = context.consumers().getBuffer(RenderType.translucent());
                renderGhostBlocks(client, pose, translucent, camera, ghostBlocks);
                renderCreateCurves(client, pose, translucent, camera);
            }
            VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
            if (previewEnabled) {
                drawCells(pose, lines, preview.clear(), camera, 0.92f, 0.22f, 0.25f, 900);
                if (!fullPreview) {
                    drawPlannedOutlines(client, pose, lines, camera);
                    drawOutlineCurves(pose, lines, camera);
                }
            }
            for (int i = 0; i < points.size(); i++) {
                SurveyPoint point = points.get(i);
                BlockPos p = point.pos();
                if (p.distToCenterSqr(camera) > 128 * 128) continue;
                boolean active = i == selected;
                LevelRenderer.renderLineBox(pose, lines, new AABB(p).move(-camera.x, -camera.y, -camera.z)
                    .inflate(active ? 0.12 : 0.05), active ? 0.3f : 1f,
                    active ? 1f : 0.82f, 0.22f, 1f);
                drawDirection(pose, lines, point, camera, active);
            }
        });
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> renderGogglesHud(graphics));
    }

    public static void openScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.screen == null) client.setScreen(new ConstructionConfigScreen());
    }

    public static boolean claimToolPress() {
        if (toolPressHeld) return false;
        toolPressHeld = true;
        return true;
    }

    public static void openQuickScreen(dev.sjimo.rrce.QuickRouteGeometry.Kind kind) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.screen == null)
            client.setScreen(new QuickRouteScreen(kind));
    }

    private static boolean holdingConstructionItem(Minecraft client) {
        return client.player != null && (client.player.getMainHandItem().is(RrceMod.TOOL)
            || client.player.getMainHandItem().is(RrceMod.PLAN)
            || client.player.getMainHandItem().is(RrceMod.DIRECTION)
            || client.player.getMainHandItem().is(RrceMod.SELECT)
            || client.player.getMainHandItem().is(RrceMod.MOVE)
            || client.player.getMainHandItem().is(RrceMod.REMOVE)
            || client.player.getMainHandItem().getItem() instanceof dev.sjimo.rrce.QuickRouteTool);
    }

    private static void drawDirection(PoseStack pose, VertexConsumer lines, SurveyPoint point,
                                      Vec3 camera, boolean active) {
        Vec3 direction = point.heading().vector();
        Vec3 sideways = new Vec3(-direction.z, 0, direction.x);
        Vec3 base = Vec3.atCenterOf(point.pos()).add(0, .2, 0).subtract(camera);
        Vec3 tip = base.add(direction.scale(2.2));
        Vec3 wing = tip.subtract(direction.scale(.65));
        float r = active ? .3f : .25f, g = active ? 1f : .88f, b = active ? .35f : .95f;
        line(pose, lines, base, tip, r, g, b);
        line(pose, lines, tip, wing.add(sideways.scale(.38)), r, g, b);
        line(pose, lines, tip, wing.subtract(sideways.scale(.38)), r, g, b);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 from, Vec3 to,
                             float red, float green, float blue) {
        dev.sjimo.rrce.client.platform.ClientApi.vertex(pose, lines, from, red, green, blue, 1f);
        dev.sjimo.rrce.client.platform.ClientApi.vertex(pose, lines, to, red, green, blue, 1f);
    }

    public static boolean shouldHideExistingBlock(BlockPos pos) {
        return shouldHideExistingBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean shouldHideExistingBlock(int x, int y, int z) {
        return hiddenExistingBlocks.contains(BlockPos.asLong(x, y, z));
    }

    private static List<Integer> visibleGhostBlocks(Minecraft client, Vec3 camera) {
        List<Integer> visible = new ArrayList<>();
        if (client.level == null) return visible;
        long[] positions = preview.blockPositions();
        int[] states = preview.blockStates();
        Block wallBlock = config.wallBlock();
        for (int priority = 0; priority < 3 && visible.size() < 1600; priority++) {
            for (int i = 0; i < Math.min(positions.length, states.length) && visible.size() < 1600; i++) {
                BlockPos p = BlockPos.of(positions[i]);
                if (p.distToCenterSqr(camera) > 80 * 80) continue;
                BlockState state = Block.stateById(states[i]);
                if (state.isAir()) continue;
                int category = state.getBlock() instanceof TrackBlock ? 0 : state.is(wallBlock) ? 1 : 2;
                if (category != priority) continue;
                if (client.level.getBlockState(p).equals(state)) continue;
                if (client.getBlockRenderer().getBlockModel(state).isCustomRenderer()) continue;
                visible.add(i);
            }
        }
        return visible;
    }

    private static void updateHiddenExistingBlocks(Minecraft client, Vec3 camera,
                                                   List<Integer> ghostBlocks, boolean active) {
        Set<Long> next = new HashSet<>();
        if (active && client.level != null) for (int index : ghostBlocks) {
            BlockPos pos = BlockPos.of(preview.blockPositions()[index]);
            if (!client.level.getBlockState(pos).isAir()) next.add(pos.asLong());
        }
        if (active && client.level != null) {
            int outlined = 0;
            for (long packed : preview.clear()) {
                BlockPos pos = BlockPos.of(packed);
                if (pos.distToCenterSqr(camera) > 96 * 96) continue;
                if (!client.level.getBlockState(pos).isAir()) next.add(packed);
                if (++outlined >= 900) break;
            }
        }
        Set<Long> previous = hiddenExistingBlocks;
        if (previous.equals(next)) return;
        hiddenExistingBlocks = Set.copyOf(next);
        if (client.level == null) return;
        Set<BlockPos> dirtySections = new HashSet<>();
        for (long packed : previous) if (!next.contains(packed)) {
            BlockPos p = BlockPos.of(packed);
            dirtySections.add(new BlockPos(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4));
        }
        for (long packed : next) if (!previous.contains(packed)) {
            BlockPos p = BlockPos.of(packed);
            dirtySections.add(new BlockPos(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4));
        }
        for (BlockPos section : dirtySections)
            client.levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
    }

    private static void renderGhostBlocks(Minecraft client, PoseStack pose, VertexConsumer translucent, Vec3 camera,
                                          List<Integer> ghostBlocks) {
        VertexConsumer ghost = new AlphaVertexConsumer(translucent, 0.48f);
        long[] positions = preview.blockPositions();
        int[] states = preview.blockStates();
        for (int i : ghostBlocks) {
            BlockPos p = BlockPos.of(positions[i]);
            BlockState state = Block.stateById(states[i]);
            pose.pushPose();
            pose.translate(p.getX() - camera.x, p.getY() - camera.y, p.getZ() - camera.z);
            var renderer = client.getBlockRenderer();
            var model = renderer.getBlockModel(state);
            renderer.getModelRenderer().renderModel(pose.last(), ghost, state, model,
                1, 1, 1, 0xF000F0, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static void drawCells(PoseStack pose, VertexConsumer lines, long[] cells, Vec3 camera,
                                  float red, float green, float blue, int limit) {
        int drawn = 0;
        for (long packed : cells) {
            BlockPos p = BlockPos.of(packed);
            if (p.distToCenterSqr(camera) > 96 * 96) continue;
            LevelRenderer.renderLineBox(pose, lines, new AABB(p).move(-camera.x, -camera.y, -camera.z)
                .inflate(0.002), red, green, blue, 0.65f);
            if (++drawn >= limit) break;
        }
    }

    private static void drawPlannedOutlines(Minecraft client, PoseStack pose, VertexConsumer lines, Vec3 camera) {
        long[] positions = preview.blockPositions();
        int[] states = preview.blockStates();
        Block wallBlock = config.wallBlock();
        int drawn = 0;
        for (int priority = 0; priority < 3 && drawn < 1800; priority++) {
            for (int i = 0; i < Math.min(positions.length, states.length) && drawn < 1800; i++) {
                BlockPos p = BlockPos.of(positions[i]);
                if (p.distToCenterSqr(camera) > 96 * 96) continue;
                BlockState state = Block.stateById(states[i]);
                if (state.isAir()) continue;
                int category = state.getBlock() instanceof TrackBlock ? 0 : state.is(wallBlock) ? 1 : 2;
                if (category != priority) continue;
                float red = category == 0 ? .22f : category == 1 ? .65f : .93f;
                float green = category == 0 ? .85f : category == 1 ? .68f : .69f;
                float blue = category == 0 ? .82f : category == 1 ? .75f : .27f;
                VoxelShape shape = state.getShape(client.level, p);
                if (shape.isEmpty()) continue;
                for (AABB box : shape.toAabbs())
                    LevelRenderer.renderLineBox(pose, lines,
                        box.move(p.getX() - camera.x, p.getY() - camera.y, p.getZ() - camera.z)
                            .inflate(.002), red, green, blue, .75f);
                drawn++;
            }
        }
    }

    private static void drawOutlineCurves(PoseStack pose, VertexConsumer lines, Vec3 camera) {
        for (BezierConnection curve : curvePreviews) {
            Vec3 origin = Vec3.atLowerCornerOf(dev.sjimo.rrce.platform.CreateApi.first(curve));
            Vec3 last = null;
            for (BezierConnection.Segment segment : curve) {
                Vec3 here = segment.position.add(origin);
                if (last != null && (here.distanceToSqr(camera) < 96 * 96 || last.distanceToSqr(camera) < 96 * 96)) {
                    Vec3 start = last.subtract(camera), end = here.subtract(camera);
                    dev.sjimo.rrce.client.platform.ClientApi.vertex(pose, lines, start, .36f, .96f, .69f, .9f);
                    dev.sjimo.rrce.client.platform.ClientApi.vertex(pose, lines, end, .36f, .96f, .69f, .9f);
                }
                last = here;
            }
        }
    }

    private static void renderCreateCurves(Minecraft client, PoseStack pose, VertexConsumer translucent, Vec3 camera) {
        VertexConsumer ghost = new AlphaVertexConsumer(translucent, 0.55f);
        for (BezierConnection stored : curvePreviews) {
            BezierConnection curve = stored.isPrimary() ? stored : stored.secondary();
            BlockPos origin = dev.sjimo.rrce.platform.CreateApi.first(curve);
            if (origin.distToCenterSqr(camera) > 160 * 160
                && dev.sjimo.rrce.platform.CreateApi.second(curve).distToCenterSqr(camera) > 160 * 160) continue;
            pose.pushPose();
            pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
            TrackRenderer.renderBezierTurn(client.level, curve, pose, ghost);
            pose.popPose();
        }
    }

    private static void renderGogglesHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!holdingConstructionItem(client) || !GogglesItem.isWearingGoggles(client.player)) return;
        int x = graphics.guiWidth() - 184;
        int y = 28;
        graphics.fill(x - 4, y - 5, x + 178, y + 62, 0xD012191A);
        graphics.fill(x - 4, y - 5, x + 178, y - 3, 0xFFB99B5D);
        graphics.fill(x - 4, y + 60, x + 178, y + 62, 0xFF5F6047);
        graphics.renderItem(AllItems.GOGGLES.asStack(), x, y - 4);
        graphics.drawString(client.font, Component.translatable("hud.rrce.title"), x + 19, y, 0xFFD6BE83);
        int width = (config.lineCount - 1) * (config.spacing + 1) + 1 + 2 * config.edgeMargin;
        graphics.drawString(client.font, Component.translatable("hud.rrce.lines", config.lineCount, width),
            x, y + 13, 0xFFE8E5D5);
        graphics.drawString(client.font, Component.translatable("hud.rrce.path", points.size(), preview.curves()),
            x, y + 26, 0xFFE8E5D5);
        Component status = !previewEnabled ? Component.translatable("hud.rrce.preview_off")
            : preview.error() != null ? Component.translatable("hud.rrce.error")
            : points.size() < 2 ? Component.translatable("hud.rrce.need_points")
            : Component.translatable("hud.rrce.ready", preview.blocks());
        graphics.drawString(client.font, status, x, y + 39, preview.error() == null ? 0xFF9ED6C8 : 0xFFFF9D87);
        if (previewEnabled && preview.error() != null)
            graphics.drawString(client.font, client.font.plainSubstrByWidth(preview.error().getString(), 174), x, y + 52, 0xFFFF9D87);
        else if (previewEnabled) graphics.drawString(client.font,
            Component.translatable(fullPreview ? "hud.rrce.full" : "hud.rrce.outline"), x, y + 52, 0xFFAAAFA7);
    }

    public static void action(String action, int value) {
        if (!ClientPlayNetworking.canSend(RrceMod.ACTION_PACKET)) return;
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(action, 32);
        buf.writeInt(value);
        ClientPlayNetworking.send(RrceMod.ACTION_PACKET, buf);
    }

}
