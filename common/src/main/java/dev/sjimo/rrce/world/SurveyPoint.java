package dev.sjimo.rrce.world;

import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;

/** One Create-style track selection: a rail block and its forward tangent. */
public record SurveyPoint(BlockPos pos, TrackHeading heading) {
    public SurveyPoint {
        pos = pos.immutable();
        if (heading == null) throw new UserFacingException("error.rrce.missing_heading");
    }
    public SurveyPoint at(BlockPos next) { return new SurveyPoint(next, heading); }
    public SurveyPoint facing(TrackHeading next) { return new SurveyPoint(pos, next); }
}
