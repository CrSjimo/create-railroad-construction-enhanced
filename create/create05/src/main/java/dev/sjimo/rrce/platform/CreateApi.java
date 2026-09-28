package dev.sjimo.rrce.platform;
import com.simibubi.create.content.trains.track.BezierConnection;
import net.minecraft.core.BlockPos;
public final class CreateApi {
    public static BlockPos first(BezierConnection curve) { return curve.tePositions.getFirst(); }
    public static BlockPos second(BezierConnection curve) { return curve.tePositions.getSecond(); }
}
