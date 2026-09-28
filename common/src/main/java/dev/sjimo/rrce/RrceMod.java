package dev.sjimo.rrce;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.sjimo.rrce.world.ConstructionExecutor;
import dev.sjimo.rrce.world.ConstructionPlan;
import dev.sjimo.rrce.world.ConstructionPlanner;
import dev.sjimo.rrce.world.CreateUnlimitedCompat;
import dev.sjimo.rrce.world.EditHistory;
import dev.sjimo.rrce.world.PlayerConstructionSettings;
import dev.sjimo.rrce.world.TrackHeading;
import dev.sjimo.rrce.world.TerrainRules;
import dev.sjimo.rrce.platform.Platform;
import dev.sjimo.rrce.platform.Events.CommandRegistrationCallback;
import dev.sjimo.rrce.platform.Events.ServerTickEvents;
import dev.sjimo.rrce.platform.Events.ServerLifecycleEvents;
import dev.sjimo.rrce.platform.Events.ServerEntityWorldChangeEvents;
import dev.sjimo.rrce.platform.Events.UseBlockCallback;
import dev.sjimo.rrce.platform.Events.ServerPlayConnectionEvents;
import dev.sjimo.rrce.platform.Network.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RrceMod {
    public static final String ID = "rrce";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final ResourceLocation CONFIG_PACKET = dev.sjimo.rrce.platform.GameApi.id(ID, "config");
    public static final ResourceLocation CONFIG_SYNC = dev.sjimo.rrce.platform.GameApi.id(ID, "config_sync");
    public static final ResourceLocation PREVIEW_SYNC = dev.sjimo.rrce.platform.GameApi.id(ID, "preview_sync");
    public static final ResourceLocation ACTION_PACKET = dev.sjimo.rrce.platform.GameApi.id(ID, "action");
    public static final ResourceLocation QUICK_CONFIG_PACKET = dev.sjimo.rrce.platform.GameApi.id(ID, "quick_config");
    public static final ResourceLocation TOOL_RELEASE_PACKET = dev.sjimo.rrce.platform.GameApi.id(ID, "tool_release");
    public static final Item TOOL = new ConstructionTool(new Item.Properties().stacksTo(1));
    public static final Item PLAN = new ConstructionPlanItem(new Item.Properties().stacksTo(1));
    public static final Item DIRECTION = new DirectionTool(new Item.Properties().stacksTo(1));
    public static final Item SELECT = new SelectPointTool(new Item.Properties().stacksTo(1));
    public static final Item MOVE = new MovePointTool(new Item.Properties().stacksTo(1));
    public static final Item REMOVE = new RemovePointTool(new Item.Properties().stacksTo(1));
    public static final Item QUICK_CURVE = new QuickRouteTool(QuickRouteGeometry.Kind.CURVE, new Item.Properties().stacksTo(1));
    public static final Item QUICK_SLOPE = new QuickRouteTool(QuickRouteGeometry.Kind.SLOPE, new Item.Properties().stacksTo(1));
    public static final Item QUICK_TURN_45 = new QuickRouteTool(QuickRouteGeometry.Kind.TURN_45, new Item.Properties().stacksTo(1));
    public static final Item QUICK_TURN_90 = new QuickRouteTool(QuickRouteGeometry.Kind.TURN_90, new Item.Properties().stacksTo(1));
    private int ticks;

    public void onInitialize() {
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "control_point_add_tool"), TOOL);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "construction_plan"), PLAN);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "control_point_direction_tool"), DIRECTION);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "control_point_select_tool"), SELECT);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "control_point_move_tool"), MOVE);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "control_point_remove_tool"), REMOVE);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "quick_curve_tool"), QUICK_CURVE);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "quick_slope_tool"), QUICK_SLOPE);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "quick_turn_45_tool"), QUICK_TURN_45);
        Platform.registerItem(dev.sjimo.rrce.platform.GameApi.id(ID, "quick_turn_90_tool"), QUICK_TURN_90);
        Platform.registerTab(dev.sjimo.rrce.platform.GameApi.id(ID, "construction"),
            Platform.creativeTabBuilder().icon(PLAN::getDefaultInstance)
                .title(Component.translatable("itemGroup.rrce.construction"))
                .displayItems((context, entries) -> {
                    entries.accept(PLAN); entries.accept(TOOL); entries.accept(SELECT);
                    entries.accept(MOVE); entries.accept(DIRECTION); entries.accept(REMOVE);
                    entries.accept(QUICK_CURVE); entries.accept(QUICK_SLOPE);
                    entries.accept(QUICK_TURN_45); entries.accept(QUICK_TURN_90);
                }).build());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (player.getItemInHand(hand).is(TOOL))
                return ((ConstructionTool) TOOL).useAt(player, world, hit.getBlockPos(), hit.getDirection());
            if (player.getItemInHand(hand).is(SELECT))
                return ((SelectPointTool) SELECT).useAt(player, world, hit.getBlockPos(), hit.getDirection());
            if (player.getItemInHand(hand).is(MOVE))
                return ((MovePointTool) MOVE).useAt(player, world, hit.getBlockPos(), hit.getDirection());
            if (player.getItemInHand(hand).is(REMOVE))
                return ((RemovePointTool) REMOVE).useAt(player, world, hit.getBlockPos(), hit.getDirection());
            if (player.getItemInHand(hand).is(DIRECTION))
                return ((DirectionTool) DIRECTION).rotate(player, world);
            if (player.getItemInHand(hand).getItem() instanceof QuickRouteTool quick)
                return quick.useAt(player, world, hand);
            if (player.getItemInHand(hand).is(PLAN)) {
                if (!ToolPress.claim(player, world)) return InteractionResult.CONSUME;
                if (world.isClientSide) dev.sjimo.rrce.client.RrceClient.openScreen();
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            SessionManager.connected(player.getUUID());
            SessionManager.get(player.getUUID()).config =
                PlayerConstructionSettings.forServer(server).get(player.getUUID());
            sync(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SessionManager.disconnected(handler.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(TOOL_RELEASE_PACKET, (server, player, handler, buf, sender) ->
            server.execute(() -> SessionManager.get(player.getUUID()).releaseToolPress()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            PlayerSession session = SessionManager.get(player.getUUID());
            session.points.clear();
            session.selectedPoint = -1;
            sync(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (++ticks % 1200 == 0) SessionManager.expire(); });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { TerrainRules.reload(); }
            catch (Exception error) { LOGGER.error("Could not load server terrain rules from {}", TerrainRules.path(), error); }
        });
        ServerPlayNetworking.registerGlobalReceiver(CONFIG_PACKET, (server, player, handler, buf, responseSender) -> {
            final net.minecraft.nbt.CompoundTag tag = buf.readNbt();
            server.execute(() -> {
                if (tag == null) return;
                if (!mayUse(player)) {
                    RrceSounds.deny(player);
                    player.sendSystemMessage(Component.translatable("error.rrce.tool_required"));
                    return;
                }
                try {
                    ConstructionConfig config = ConstructionConfig.load(tag);
                    validateConfig(config);
                    SessionManager.get(player.getUUID()).config = config;
                    sync(player);
                } catch (RuntimeException error) {
                    RrceSounds.deny(player);
                    if (!(error instanceof UserFacingException)) LOGGER.warn("Invalid railway plan settings", error);
                    player.sendSystemMessage(Component.translatable("message.rrce.invalid_plan",
                        UserFacingException.display(error)));
                    sync(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION_PACKET, (server, player, handler, buf, responseSender) -> {
            String action = buf.readUtf(32);
            int value = buf.readInt();
            server.execute(() -> {
                if (!mayUse(player)) {
                    RrceSounds.deny(player);
                    player.sendSystemMessage(Component.translatable("error.rrce.tool_required"));
                    return;
                }
                try { handleAction(player, action, value); }
                catch (RuntimeException error) {
                    RrceSounds.deny(player);
                    if (!(error instanceof UserFacingException)) LOGGER.warn("Railway action failed", error);
                    player.sendSystemMessage(Component.translatable("message.rrce.operation_failed",
                        UserFacingException.display(error)));
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(QUICK_CONFIG_PACKET, (server, player, handler, buf, responseSender) -> {
            String kindId = buf.readUtf(32);
            double first = buf.readDouble();
            double second = buf.readDouble();
            server.execute(() -> {
                try {
                    if (player.isSpectator() || !(player.getMainHandItem().getItem() instanceof QuickRouteTool tool)
                        || !tool.kind.id.equals(kindId))
                        throw new UserFacingException("error.rrce.quick_tool_required");
                    QuickRouteTool.save(player.getMainHandItem(), tool.kind,
                        new QuickRouteGeometry.Settings(first, second));
                    player.sendSystemMessage(Component.translatable("message.rrce.quick_settings_saved"));
                } catch (RuntimeException error) {
                    RrceSounds.deny(player);
                    if (!(error instanceof UserFacingException)) LOGGER.warn("Quick route settings failed", error);
                    player.sendSystemMessage(Component.translatable("message.rrce.operation_failed",
                        UserFacingException.display(error)));
                }
            });
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(commands()));
        LOGGER.info("Create Railroad Construction Enhanced initialized");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rrce")
            .requires(source -> source.getEntity() instanceof ServerPlayer || source.hasPermission(2));
        root.then(Commands.literal("build").executes(ctx -> run(ctx.getSource(), s -> build(s, SessionManager.get(s.getUUID())))));
        root.then(Commands.literal("preview").executes(ctx -> run(ctx.getSource(), s -> {
            PlayerSession session = SessionManager.get(s.getUUID());
            session.previewEnabled = !session.previewEnabled;
            sync(s);
            s.sendSystemMessage(Component.translatable("message.rrce.preview_changed",
                Component.translatable(session.previewEnabled ? "screen.rrce.yes" : "screen.rrce.no")));
        })));
        root.then(Commands.literal("clear").executes(ctx -> run(ctx.getSource(), s -> {
            SessionManager.get(s.getUUID()).points.clear();
            SessionManager.get(s.getUUID()).selectedPoint = -1;
            syncPointEdit(s, true);
            s.sendSystemMessage(Component.translatable("message.rrce.points_cleared"));
        })));
        root.then(Commands.literal("pop").executes(ctx -> run(ctx.getSource(), s -> {
            var points = SessionManager.get(s.getUUID()).points;
            if (points.isEmpty()) throw new UserFacingException("error.rrce.no_points");
            points.remove(points.size() - 1);
            SessionManager.get(s.getUUID()).selectedPoint = points.size() - 1;
            syncPointEdit(s, true);
            s.sendSystemMessage(Component.translatable("message.rrce.point_removed", points.size()));
        })));
        root.then(Commands.literal("point")
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> run(ctx.getSource(), s -> {
                            BlockPos p = new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
                            validatePointPosition(s, p);
                            TrackHeading heading = TrackHeading.fromVector(s.getLookAngle());
                            SessionManager.get(s.getUUID()).addPoint(p, heading);
                            syncPointEdit(s, false);
                            s.sendSystemMessage(Component.translatable("message.rrce.point_added_direction",
                                p.toShortString(), heading.displayName()));
                        }))))));
        root.then(Commands.literal("undo").executes(ctx -> run(ctx.getSource(), s ->
            undo(s, SessionManager.get(s.getUUID())))));
        root.then(Commands.literal("redo").executes(ctx -> run(ctx.getSource(), s ->
            redo(s, SessionManager.get(s.getUUID())))));
        root.then(Commands.literal("status").executes(ctx -> run(ctx.getSource(), s -> {
            PlayerSession session = SessionManager.get(s.getUUID());
            s.sendSystemMessage(Component.translatable("message.rrce.status", session.points.size(),
                session.config.lineCount, session.config.spacing, session.undo.size()));
        })));
        root.then(Commands.literal("materials").executes(ctx -> run(ctx.getSource(), s ->
            s.sendSystemMessage(Component.translatable("message.rrce.available_tracks", String.join(", ",
                TrackMaterial.ALL.keySet().stream().map(ResourceLocation::toString).sorted().toList()))))));
        root.then(Commands.literal("terrain").then(Commands.literal("reload")
            .requires(source -> source.hasPermission(2)).executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                try {
                    TerrainRules.reload();
                    for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) sync(player);
                    source.sendSuccess(() -> Component.translatable("message.rrce.terrain_rules_reloaded",
                        TerrainRules.path().toString()), true);
                    return 1;
                } catch (Exception error) {
                    LOGGER.warn("Could not reload server terrain rules", error);
                    source.sendFailure(Component.translatable("error.rrce.terrain_rules_reload"));
                    return 0;
                }
            })));
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config");
        numeric(config, "lines", 1, 16, (c, n) -> c.resizeLines(n));
        numeric(config, "spacing", 1, 32, (c, n) -> c.spacing = n);
        numeric(config, "margin", 0, 16, (c, n) -> c.edgeMargin = n);
        numeric(config, "threshold", 1, 64, (c, n) -> c.obstacleThreshold = n);
        numeric(config, "tunnelHeight", 3, 32, (c, n) -> c.tunnelHeight = n);
        numeric(config, "tunnelSide", 0, 16, (c, n) -> c.tunnelSideClearance = n);
        numeric(config, "wallThickness", 1, 8, (c, n) -> c.wallThickness = n);
        numeric(config, "roofThickness", 1, 8, (c, n) -> c.roofThickness = n);
        config.then(Commands.literal("cutAngle").then(Commands.argument("value", DoubleArgumentType.doubleArg(5, 85))
            .executes(ctx -> run(ctx.getSource(), player -> {
                SessionManager.get(player.getUUID()).config.cutAngle = DoubleArgumentType.getDouble(ctx, "value");
                sync(player);
            }))));
        numeric(config, "replaceFoundation", 0, 1, (c, n) -> c.replaceFoundation = n == 1);
        numeric(config, "terrainWork", 0, 1, (c, n) -> c.terrainWork = n == 1);
        config.then(Commands.literal("foundation").then(Commands.argument("id", StringArgumentType.word())
            .executes(ctx -> run(ctx.getSource(), s -> {
                ConstructionConfig c = SessionManager.get(s.getUUID()).config;
                String previous = c.foundation;
                c.foundation = StringArgumentType.getString(ctx, "id");
                try { validateConfig(c); } catch (RuntimeException error) { c.foundation = previous; throw error; }
                sync(s);
            }))));
        config.then(Commands.literal("wall").then(Commands.argument("id", StringArgumentType.word())
            .executes(ctx -> run(ctx.getSource(), s -> {
                ConstructionConfig c = SessionManager.get(s.getUUID()).config;
                String previous = c.wall;
                c.wall = StringArgumentType.getString(ctx, "id");
                try { validateConfig(c); } catch (RuntimeException error) { c.wall = previous; throw error; }
                sync(s);
            }))));
        config.then(Commands.literal("material").then(Commands.argument("line", IntegerArgumentType.integer(1, 16))
            .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> run(ctx.getSource(), s -> {
                ConstructionConfig c = SessionManager.get(s.getUUID()).config;
                int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                if (i >= c.lineCount) throw new UserFacingException("error.rrce.invalid_line_index");
                String id = StringArgumentType.getString(ctx, "id");
                if (!TrackMaterial.ALL.containsKey(ResourceLocation.tryParse(id)))
                    throw new UserFacingException("error.rrce.unknown_track", id);
                c.materials.set(i, id); sync(s);
            })))));
        config.then(Commands.literal("primary").then(Commands.argument("line", IntegerArgumentType.integer(1, 16))
            .then(Commands.argument("start", IntegerArgumentType.integer(0, 1)).executes(ctx -> run(ctx.getSource(), s -> {
                ConstructionConfig c = SessionManager.get(s.getUUID()).config;
                int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                if (i >= c.lineCount) throw new UserFacingException("error.rrce.invalid_line_index");
                c.primaryAtStart.set(i, IntegerArgumentType.getInteger(ctx, "start") == 1); sync(s);
            })))));
        root.then(config);
        return root;
    }

    private interface ConfigSetter { void set(ConstructionConfig c, int value); }
    private static void numeric(LiteralArgumentBuilder<CommandSourceStack> root, String name, int min, int max, ConfigSetter setter) {
        root.then(Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max))
            .executes(ctx -> run(ctx.getSource(), player -> {
                ConstructionConfig c = SessionManager.get(player.getUUID()).config;
                setter.set(c, IntegerArgumentType.getInteger(ctx, "value")); sync(player);
            }))));
        root.then(Commands.literal("direction").then(Commands.argument("step", IntegerArgumentType.integer(0, 7))
            .executes(ctx -> run(ctx.getSource(), s -> {
                PlayerSession session = SessionManager.get(s.getUUID());
                session.select(session.selectedPoint);
                var selected = session.points.get(session.selectedPoint);
                session.points.set(session.selectedPoint,
                    selected.facing(TrackHeading.byIndex(IntegerArgumentType.getInteger(ctx, "step"))));
                syncPointEdit(s, false);
            }))));
    }

    private interface PlayerAction { void run(ServerPlayer player); }
    private static int run(CommandSourceStack source, PlayerAction action) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!mayUse(player)) throw new UserFacingException("error.rrce.tool_required");
            action.run(player); return 1;
        } catch (Exception e) {
            if (source.getEntity() instanceof ServerPlayer player) RrceSounds.deny(player);
            if (e instanceof RuntimeException runtime) {
                if (!(runtime instanceof UserFacingException)) LOGGER.warn("Railway command failed", runtime);
                source.sendFailure(UserFacingException.display(runtime));
            } else {
                LOGGER.warn("Railway command failed", e);
                source.sendFailure(Component.translatable("error.rrce.unexpected"));
            }
            return 0;
        }
    }

    public static boolean mayUse(ServerPlayer player) {
        return !player.isSpectator() && (player.hasPermissions(2) || player.getMainHandItem().is(TOOL)
            || player.getMainHandItem().is(PLAN) || player.getMainHandItem().is(DIRECTION)
            || player.getMainHandItem().is(SELECT) || player.getMainHandItem().is(MOVE)
            || player.getMainHandItem().is(REMOVE)
            || player.getMainHandItem().getItem() instanceof QuickRouteTool);
    }

    public static void validatePointPosition(ServerPlayer player, BlockPos position) {
        if (position.getY() < player.serverLevel().getMinBuildHeight()
            || position.getY() >= player.serverLevel().getMaxBuildHeight()
            || !player.serverLevel().hasChunkAt(position))
            throw new UserFacingException("error.rrce.position_unavailable", position.toShortString());
    }

    public static void build(ServerPlayer player, PlayerSession session) {
        if (!mayUse(player)) throw new UserFacingException("error.rrce.tool_required");
        ConstructionPlan plan = ConstructionPlanner.plan(player.serverLevel(), session.points, session.config,
            CreateUnlimitedCompat.relaxPlacementChecks(player));
        EditHistory edit = ConstructionExecutor.execute(player.serverLevel(), plan);
        RrceSounds.place(player);
        session.remember(edit);
        session.points.clear();
        session.selectedPoint = -1;
        sync(player);
        player.sendSystemMessage(Component.translatable("message.rrce.built", edit.size(), plan.curves.size()));
    }

    private static void undo(ServerPlayer player, PlayerSession session) {
        PlayerSession.UndoResult result = session.undoConstruction(player.serverLevel());
        RrceSounds.breakBlock(player);
        player.sendSystemMessage(Component.translatable("message.rrce.undone", result.edit().size()));
        if (result.pointsRestored())
            player.sendSystemMessage(Component.translatable("message.rrce.points_restored", session.points.size()));
        sync(player);
    }

    private static void redo(ServerPlayer player, PlayerSession session) {
        EditHistory edit = session.redoConstruction(player.serverLevel());
        RrceSounds.place(player);
        player.sendSystemMessage(Component.translatable("message.rrce.redone", edit.size()));
        sync(player);
    }

    private static void handleAction(ServerPlayer player, String action, int value) {
        PlayerSession session = SessionManager.get(player.getUUID());
        switch (action) {
            case "preview" -> session.previewEnabled = !session.previewEnabled;
            case "fullPreview" -> session.fullPreview = !session.fullPreview;
            case "select" -> { session.select(value); RrceSounds.rotate(player); }
            case "remove" -> session.removeSelected();
            case "clear" -> {
                session.points.clear(); session.selectedPoint = -1;
            }
            case "pop" -> {
                if (session.points.isEmpty()) throw new UserFacingException("error.rrce.no_points");
                session.points.remove(session.points.size() - 1);
                session.selectedPoint = session.points.size() - 1;
            }
            case "x+", "x-", "y+", "y-", "z+", "z-" -> {
                session.select(session.selectedPoint);
                BlockPos old = session.points.get(session.selectedPoint).pos();
                BlockPos moved = switch (action) {
                    case "x+" -> old.east(); case "x-" -> old.west();
                    case "y+" -> old.above(); case "y-" -> old.below();
                    case "z+" -> old.south(); default -> old.north();
                };
                validatePointPosition(player, moved);
                session.moveSelected(moved);
            }
            case "rotate+", "rotate-" -> session.rotateSelected(action.equals("rotate+") ? 1 : -1);
            case "build" -> { build(player, session); return; }
            case "undo" -> { undo(player, session); return; }
            case "redo" -> { redo(player, session); return; }
            default -> throw new UserFacingException("error.rrce.unknown_action", action);
        }
        boolean removedPoint = action.equals("remove") || action.equals("clear") || action.equals("pop");
        boolean changedPoint = removedPoint || action.equals("rotate+") || action.equals("rotate-")
            || action.equals("x+") || action.equals("x-") || action.equals("y+") || action.equals("y-")
            || action.equals("z+") || action.equals("z-");
        if (changedPoint) {
            syncPointEdit(player, removedPoint);
            return;
        }
        if (action.equals("select")) syncState(player);
        else sync(player);
    }

    private static void validateConfig(ConstructionConfig c) {
        for (String id : new String[] {c.foundation, c.wall}) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)
                || !BuiltInRegistries.BLOCK.get(key).defaultBlockState().blocksMotion())
                throw new UserFacingException("error.rrce.invalid_solid_block", id);
        }
        for (String material : c.materials)
            if (!material.isBlank() && !TrackMaterial.ALL.containsKey(ResourceLocation.tryParse(material)))
                throw new UserFacingException("error.rrce.unknown_track", material);
    }

    public static void sync(ServerPlayer player) {
        syncState(player);
        refreshPreview(player, SessionManager.get(player.getUUID()), false);
    }

    /** The same plan check drives both the preview and one edit feedback sound. */
    public static void syncPointEdit(ServerPlayer player, boolean removed) {
        syncState(player);
        boolean buildable = refreshPreview(player, SessionManager.get(player.getUUID()), true);
        if (!buildable) RrceSounds.deny(player);
        else if (removed) RrceSounds.remove(player);
        else RrceSounds.rotate(player);
    }

    private static void syncState(ServerPlayer player) {
        PlayerSession session = SessionManager.get(player.getUUID());
        PlayerConstructionSettings.forServer(player.getServer()).put(player.getUUID(), session.config);
        CompoundTag state = new CompoundTag();
        state.put("config", session.config.save());
        state.putLongArray("points", session.points.stream().mapToLong(p -> p.pos().asLong()).toArray());
        state.putIntArray("headings", session.points.stream().mapToInt(p -> p.heading().ordinal()).toArray());
        state.putInt("selected", session.selectedPoint);
        state.putBoolean("preview", session.previewEnabled);
        state.putBoolean("fullPreview", session.fullPreview);
        FriendlyByteBuf buf = dev.sjimo.rrce.platform.Network.PacketByteBufs.create();
        buf.writeNbt(state);
        ServerPlayNetworking.send(player, CONFIG_SYNC, buf);
    }

    private static boolean refreshPreview(ServerPlayer player, PlayerSession session, boolean checkWhenDisabled) {
        int revision = ++session.previewRevision;
        CompoundTag header = new CompoundTag();
        header.putInt("revision", revision);
        header.putString("kind", "header");
        if ((session.previewEnabled || checkWhenDisabled) && session.points.size() >= 2) {
            try {
                ConstructionPlan plan = ConstructionPlanner.plan(player.serverLevel(), session.points, session.config,
                    CreateUnlimitedCompat.relaxPlacementChecks(player));
                if (session.previewEnabled) {
                    header.putInt("blocks", plan.blocks.size());
                    header.putInt("curves", plan.curves.size());
                    sendPreviewPacket(player, header);
                    sendPreviewCells(player, revision, "clear", plan.blocks.entrySet().stream()
                        .filter(e -> e.getValue().isAir()).mapToLong(e -> e.getKey().asLong()).toArray());
                    sendPreviewBlocks(player, revision, plan);
                    sendPreviewCurves(player, revision, plan);
                } else sendPreviewPacket(player, header);
                return true;
            } catch (RuntimeException error) {
                if (!(error instanceof UserFacingException)) LOGGER.warn("Railway preview failed", error);
                header.putString("error", dev.sjimo.rrce.platform.GameApi.toJson(UserFacingException.display(error)));
                sendPreviewPacket(player, header);
                return false;
            }
        }
        sendPreviewPacket(player, header);
        return true;
    }

    private static void sendPreviewCells(ServerPlayer player, int revision, String kind, long[] positions) {
        for (int start = 0; start < positions.length; start += 8192) {
            int size = Math.min(8192, positions.length - start);
            long[] part = new long[size];
            System.arraycopy(positions, start, part, 0, size);
            CompoundTag tag = new CompoundTag();
            tag.putInt("revision", revision);
            tag.putString("kind", kind);
            tag.putLongArray("cells", part);
            sendPreviewPacket(player, tag);
        }
    }

    private static void sendPreviewBlocks(ServerPlayer player, int revision, ConstructionPlan plan) {
        var entries = plan.blocks.entrySet().stream().filter(e -> !e.getValue().isAir()).toList();
        for (int start = 0; start < entries.size(); start += 2048) {
            int size = Math.min(2048, entries.size() - start);
            long[] positions = new long[size];
            int[] states = new int[size];
            for (int i = 0; i < size; i++) {
                var entry = entries.get(start + i);
                positions[i] = entry.getKey().asLong();
                states[i] = Block.getId(entry.getValue());
            }
            CompoundTag tag = new CompoundTag();
            tag.putInt("revision", revision);
            tag.putString("kind", "blocks");
            tag.putLongArray("cells", positions);
            tag.putIntArray("states", states);
            sendPreviewPacket(player, tag);
        }
    }

    private static void sendPreviewCurves(ServerPlayer player, int revision, ConstructionPlan plan) {
        for (var curve : plan.curves) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("revision", revision);
            tag.putString("kind", "curve");
            tag.put("connection", curve.write(BlockPos.ZERO));
            sendPreviewPacket(player, tag);
        }
    }

    private static void sendPreviewPacket(ServerPlayer player, CompoundTag tag) {
        FriendlyByteBuf buf = dev.sjimo.rrce.platform.Network.PacketByteBufs.create();
        buf.writeNbt(tag);
        ServerPlayNetworking.send(player, PREVIEW_SYNC, buf);
    }

}
