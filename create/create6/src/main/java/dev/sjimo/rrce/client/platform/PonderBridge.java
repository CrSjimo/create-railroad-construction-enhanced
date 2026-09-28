package dev.sjimo.rrce.client.platform;

import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class PonderBridge {
    @FunctionalInterface public interface Story { void run(SceneBuilder scene, SceneBuildingUtil util); }
    private record Entry(ResourceLocation item, ResourceLocation stage, Story story) {}
    public static final class Registration implements PonderPlugin {
        private final String id;
        private final List<Entry> entries = new ArrayList<>();
        Registration(String id) { this.id = id; }
        public void addStoryBoard(ResourceLocation item, ResourceLocation stage, Story story) { entries.add(new Entry(item, stage, story)); }
        @Override public String getModId() { return id; }
        @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
            for (Entry entry : entries) helper.forComponents(entry.item).addStoryBoard(entry.stage, entry.story::run);
        }
    }
    public static Registration registration(String id) {
        Registration registration = new Registration(id);
        PonderIndex.addPlugin(registration);
        return registration;
    }
    public static InputElementBuilder controls(SceneBuilder scene, Vec3 position, Pointing direction, int duration) {
        return scene.overlay().showControls(position, direction, duration);
    }
}
