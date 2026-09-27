package dev.sjimo.rrce.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.RrceMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ConstructionConfigScreen extends Screen {
    private final ConstructionConfig config = ConstructionConfig.load(RrceClient.config.save());
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private int page;
    private int selectedLine;
    private String message = "";
    private int left, top, panelWidth, columnWidth;
    private Button materialUp, materialDown;

    public ConstructionConfigScreen() { super(Component.translatable("screen.rrce.title")); }

    @Override
    protected void init() {
        clearWidgets(); fields.clear();
        materialUp = materialDown = null;
        panelWidth = Math.min(340, width - 20);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 238) / 2);
        columnWidth = (panelWidth - 8) / 2;
        String[] tabs = {"route", "terrain", "tunnel", "materials", "points"};
        int tabWidth = panelWidth / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            addRenderableWidget(Button.builder(t("screen.rrce.tab." + tabs[i]), b -> {
                if (savePage()) { page = target; rebuildWidgets(); }
            }).bounds(left + i * tabWidth, top + 25, tabWidth - 2, 20).build());
        }
        switch (page) {
            case 0 -> routePage();
            case 1 -> terrainPage();
            case 2 -> tunnelPage();
            case 3 -> materialsPage();
            default -> pointsPage();
        }
        int footer = top + 185;
        int gap = 4;
        int buttonWidth = (panelWidth - gap * 4) / 5;
        String[] actions = {"save", "build", "undo", "redo", "close"};
        for (int i = 0; i < actions.length; i++) {
            String action = actions[i];
            addRenderableWidget(Button.builder(t("screen.rrce." + action), b -> footerAction(action))
                .bounds(left + i * (buttonWidth + gap), footer, buttonWidth, 20).build());
        }
    }

    private void routePage() {
        addField("lines", Integer.toString(config.lineCount), 0, 0);
        addField("spacing", Integer.toString(config.spacing), 1, 0);
        addField("margin", Integer.toString(config.edgeMargin), 0, 1);
        addRenderableWidget(Button.builder(previewLabel(), b -> {
            RrceClient.action("preview", 0); RrceClient.previewEnabled = !RrceClient.previewEnabled;
            b.setMessage(previewLabel());
        }).bounds(left, top + 151, columnWidth, 20).build());
        addRenderableWidget(Button.builder(fullPreviewLabel(), b -> {
            RrceClient.action("fullPreview", 0); RrceClient.fullPreview = !RrceClient.fullPreview;
            b.setMessage(fullPreviewLabel());
        }).bounds(left + columnWidth + 8, top + 151, columnWidth, 20).build());
    }

    private void terrainPage() {
        addField("threshold", Integer.toString(config.obstacleThreshold), 0, 0);
        addField("cutAngle", String.format(java.util.Locale.ROOT, "%.2f", config.cutAngle), 1, 0);
        addRenderableWidget(Button.builder(toggleLabel("terrainWork", config.terrainWork), b -> {
            config.terrainWork = !config.terrainWork;
            b.setMessage(toggleLabel("terrainWork", config.terrainWork));
        }).bounds(left, top + 111, columnWidth, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("replaceFoundation", config.replaceFoundation), b -> {
            config.replaceFoundation = !config.replaceFoundation;
            b.setMessage(toggleLabel("replaceFoundation", config.replaceFoundation));
        }).bounds(left + columnWidth + 8, top + 111, columnWidth, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("allBlocksTerrain", config.allBlocksTerrain), b -> {
            config.allBlocksTerrain = !config.allBlocksTerrain;
            b.setMessage(toggleLabel("allBlocksTerrain", config.allBlocksTerrain));
        }).bounds(left, top + 151, panelWidth, 20).build());
    }

    private void tunnelPage() {
        addField("tunnelHeight", Integer.toString(config.tunnelHeight), 0, 0);
        addField("tunnelSide", Integer.toString(config.tunnelSideClearance), 1, 0);
        addField("wallThickness", Integer.toString(config.wallThickness), 0, 1);
        addField("roofThickness", Integer.toString(config.roofThickness), 1, 1);
    }

    private void materialsPage() {
        int right = left + columnWidth + 8;
        addRenderableWidget(Button.builder(t("screen.rrce.previous_line"), b -> {
            if (savePage()) { selectedLine = (selectedLine - 1 + config.lineCount) % config.lineCount; rebuildWidgets(); }
        }).bounds(left, top + 68, columnWidth, 20).build());
        addRenderableWidget(Button.builder(t("screen.rrce.next_line"), b -> {
            if (savePage()) { selectedLine = (selectedLine + 1) % config.lineCount; rebuildWidgets(); }
        }).bounds(right, top + 68, columnWidth, 20).build());
        addFieldAt("foundation", config.foundation, left, top + 111, columnWidth);
        addFieldAt("wall", config.wall, right, top + 111, columnWidth);
        int materialY = top + 150;
        addFieldAt("material", config.materials.get(selectedLine), left + 22, materialY,
            columnWidth - 44);
        fields.get("material").setHint(t("screen.rrce.no_track"));
        int arrowsX = left + columnWidth - 20;
        materialUp = addRenderableWidget(Button.builder(Component.empty(), b -> cycleMaterial(-1))
            .bounds(arrowsX, materialY, 20, 10).build());
        materialUp.setTooltip(Tooltip.create(t("screen.rrce.previous_track")));
        materialDown = addRenderableWidget(Button.builder(Component.empty(), b -> cycleMaterial(1))
            .bounds(arrowsX, materialY + 10, 20, 10).build());
        materialDown.setTooltip(Tooltip.create(t("screen.rrce.next_track")));
        addRenderableWidget(Button.builder(primaryLabel(), b -> {
            config.primaryAtStart.set(selectedLine, !config.primaryAtStart.get(selectedLine));
            b.setMessage(primaryLabel());
        }).bounds(right, materialY, columnWidth, 20).build());
    }

    private void pointsPage() {
        addRenderableWidget(Button.builder(t("screen.rrce.previous_point"), b -> selectPoint(-1))
            .bounds(left, top + 76, 72, 20).build());
        addRenderableWidget(Button.builder(t("screen.rrce.next_point"), b -> selectPoint(1))
            .bounds(left + 78, top + 76, 72, 20).build());
        addRenderableWidget(Button.builder(t("screen.rrce.remove"), b -> RrceClient.action("remove", 0))
            .bounds(left + 156, top + 76, panelWidth - 156, 20).build());
        String[] moves = {"x-", "x+", "y-", "y+", "z-", "z+"};
        String[] names = {"west", "east", "down", "up", "north", "south"};
        int moveWidth = (panelWidth - 10) / 3;
        for (int i = 0; i < moves.length; i++) {
            String move = moves[i];
            addRenderableWidget(Button.builder(t("screen.rrce." + names[i]), b -> RrceClient.action(move, 0))
                .bounds(left + (i % 3) * (moveWidth + 5), top + 103 + (i / 3) * 25, moveWidth, 20).build());
        }
        addRenderableWidget(Button.builder(t("screen.rrce.rotate_left"), b -> RrceClient.action("rotate-", 0))
            .bounds(left, top + 156, 106, 20).build());
        addRenderableWidget(Button.builder(t("screen.rrce.rotate_right"), b -> RrceClient.action("rotate+", 0))
            .bounds(left + 110, top + 156, 106, 20).build());
        addRenderableWidget(Button.builder(t("screen.rrce.clear"), b -> RrceClient.action("clear", 0))
            .bounds(left + 220, top + 156, panelWidth - 220, 20).build());
    }

    private void addField(String name, String value, int column, int row) {
        int x = left + column * (columnWidth + 8);
        int y = top + 72 + row * 43;
        addFieldAt(name, value, x, y, columnWidth);
    }

    private void addFieldAt(String name, String value, int x, int y, int boxWidth) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, t("screen.rrce.field." + name));
        box.setMaxLength(128);
        box.setValue(value);
        fields.put(name, box);
        addRenderableWidget(box);
    }

    private boolean savePage() {
        try {
            switch (page) {
                case 0 -> {
                    config.resizeLines(number("lines", 1, 16));
                    selectedLine = Math.min(selectedLine, config.lineCount - 1);
                    config.spacing = number("spacing", 1, 32);
                    config.edgeMargin = number("margin", 0, 16);
                }
                case 1 -> {
                    config.obstacleThreshold = number("threshold", 1, 64);
                    config.cutAngle = decimal("cutAngle", 5, 85);
                }
                case 2 -> {
                    config.tunnelHeight = number("tunnelHeight", 3, 32);
                    config.tunnelSideClearance = number("tunnelSide", 0, 16);
                    config.wallThickness = number("wallThickness", 1, 8);
                    config.roofThickness = number("roofThickness", 1, 8);
                }
                case 3 -> {
                    config.foundation = value("foundation");
                    config.wall = value("wall");
                    config.materials.set(selectedLine, value("material"));
                }
                default -> { }
            }
            message = "";
            return true;
        } catch (RuntimeException error) {
            String field = error.getMessage();
            message = fields.containsKey(field)
                ? t("screen.rrce.invalid_field", t("screen.rrce.field." + field)).getString()
                : t("screen.rrce.invalid_value").getString();
            return false;
        }
    }

    private int number(String key, int min, int max) {
        try {
            int parsed = Integer.parseInt(value(key));
            if (parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key);
    }
    private double decimal(String key, double min, double max) {
        try {
            double parsed = Double.parseDouble(value(key));
            if (Double.isFinite(parsed) && parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key);
    }
    private String value(String key) { return fields.get(key).getValue().trim(); }
    private Component t(String key, Object... args) { return Component.translatable(key, args); }
    private Component toggleLabel(String key, boolean enabled) {
        return t("screen.rrce." + key, t(enabled ? "screen.rrce.yes" : "screen.rrce.no"));
    }
    private Component previewLabel() { return toggleLabel("preview", RrceClient.previewEnabled); }
    private Component fullPreviewLabel() { return t("screen.rrce.preview_mode",
        t(RrceClient.fullPreview ? "screen.rrce.full" : "screen.rrce.outline")); }
    private Component primaryLabel() { return t("screen.rrce.primary",
        t(config.primaryAtStart.get(selectedLine) ? "screen.rrce.start" : "screen.rrce.end")); }

    private void selectPoint(int direction) {
        int count = RrceClient.points.size();
        if (count == 0) return;
        int next = (Math.max(0, RrceClient.selected) + direction + count) % count;
        RrceClient.selected = next;
        RrceClient.action("select", next);
        rebuildWidgets();
    }

    private void cycleMaterial(int direction) {
        List<String> available = new ArrayList<>();
        available.add("");
        available.addAll(TrackMaterial.ALL.keySet().stream().map(Object::toString).sorted().toList());
        EditBox material = fields.get("material");
        int current = available.indexOf(material.getValue());
        int next = current < 0 ? direction > 0 ? 1 : available.size() - 1
            : Math.floorMod(current + direction, available.size());
        material.setValue(available.get(next));
    }

    private boolean apply() {
        if (!savePage()) return false;
        for (String id : new String[] {config.foundation, config.wall}) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)
                || !BuiltInRegistries.BLOCK.get(key).defaultBlockState().blocksMotion()) {
                message = t("screen.rrce.invalid_block", id).getString();
                return false;
            }
        }
        for (String id : config.materials)
            if (!id.isBlank() && !TrackMaterial.ALL.containsKey(ResourceLocation.tryParse(id))) {
                message = t("screen.rrce.invalid_track", id).getString();
                return false;
            }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(config.save());
        ClientPlayNetworking.send(RrceMod.CONFIG_PACKET, buf);
        RrceClient.config = ConstructionConfig.load(config.save());
        message = t("screen.rrce.saved").getString();
        return true;
    }

    private void footerAction(String action) {
        switch (action) {
            case "save" -> apply();
            case "build" -> { if (apply()) { RrceClient.action("build", 0); onClose(); } }
            case "undo", "redo" -> RrceClient.action(action, 0);
            case "close" -> onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 7, top - 6, left + panelWidth + 7, top + 224, 0xD5182022);
        graphics.fill(left - 7, top - 6, left + panelWidth + 7, top - 4, 0xFFB99B5D);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left, top + 7, 0xFFE9D8AE);
        for (Map.Entry<String, EditBox> entry : fields.entrySet()) {
            if (page == 3 && entry.getKey().equals("material")) continue;
            graphics.drawString(font, t("screen.rrce.field." + entry.getKey()),
                entry.getValue().getX(), entry.getValue().getY() - 12, 0xFFD8D6C8);
        }
        if (page == 3) {
            graphics.drawString(font, t("screen.rrce.line_number", selectedLine + 1, config.lineCount),
                left, top + 54, 0xFFBCD0CA);
            graphics.drawString(font, t("screen.rrce.field.material"), left, top + 138, 0xFFD8D6C8);
            graphics.fill(left, top + 150, left + 20, top + 170, 0xFF343D3D);
            ResourceLocation key = ResourceLocation.tryParse(value("material"));
            TrackMaterial material = key == null ? null : TrackMaterial.ALL.get(key);
            if (material != null) graphics.renderItem(material.asStack(), left + 2, top + 152);
            drawTriangle(graphics, materialUp, true);
            drawTriangle(graphics, materialDown, false);
        }
        if (page == 0) {
            graphics.drawString(font, font.plainSubstrByWidth(t("screen.rrce.route_hint").getString(), panelWidth),
                left, top + 138, 0xFFBCD0CA);
            graphics.drawString(font, font.plainSubstrByWidth(t("screen.rrce.turn_hint").getString(), panelWidth),
                left, top + 173, 0xFFBCD0CA);
        }
        if (page == 1) graphics.drawString(font,
            font.plainSubstrByWidth(t("screen.rrce.terrain_rules_hint").getString(), panelWidth),
            left, top + 138, 0xFFBCD0CA);
        if (page == 4) {
            int count = RrceClient.points.size();
            Component pointStatus = count == 0 ? t("screen.rrce.no_points")
                : t("screen.rrce.selected_point", Math.max(0, RrceClient.selected) + 1, count,
                    RrceClient.points.get(Math.max(0, RrceClient.selected)).pos().toShortString() + " · "
                        + RrceClient.points.get(Math.max(0, RrceClient.selected)).heading().displayName().getString());
            graphics.drawString(font, font.plainSubstrByWidth(pointStatus.getString(), panelWidth),
                left, top + 61, 0xFFBCD0CA);
        }
        if (!message.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(message, panelWidth),
            left, top + 210, 0xFFFFB28B);
    }

    private static void drawTriangle(GuiGraphics graphics, Button button, boolean up) {
        if (button == null) return;
        int color = button.isHoveredOrFocused() ? 0xFFFFE4A0 : 0xFFE1D9B8;
        int centerX = button.getX() + button.getWidth() / 2;
        int startY = button.getY() + 3;
        for (int row = 0; row < 5; row++) {
            int halfWidth = up ? row : 4 - row;
            graphics.fill(centerX - halfWidth, startY + row,
                centerX + halfWidth + 1, startY + row + 1, color);
        }
    }
}
