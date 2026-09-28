package dev.sjimo.rrce.client.platform;
import com.simibubi.create.foundation.ponder.PonderRegistrationHelper;
import com.simibubi.create.foundation.ponder.SceneBuilder;
import com.simibubi.create.foundation.ponder.element.InputWindowElement;
import com.simibubi.create.foundation.utility.Pointing;
import net.minecraft.world.phys.Vec3;
public final class PonderBridge {
    public static PonderRegistrationHelper registration(String id) { return new PonderRegistrationHelper(id); }
    public static InputWindowElement controls(SceneBuilder scene, Vec3 position, Pointing direction, int duration) {
        InputWindowElement input = new InputWindowElement(position, direction);
        scene.overlay.showControls(input, duration);
        return input;
    }
}
