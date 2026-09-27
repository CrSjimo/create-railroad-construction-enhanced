package dev.sjimo.rrce;

import dev.sjimo.rrce.world.SurveyPoint;
import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Places a second Create-style rail selection on the block lattice. */
public final class QuickRouteGeometry {
    public enum Kind {
        CURVE("quick_curve_tool", 20, .05),
        SLOPE("quick_slope_tool", 20, .05),
        TURN_45("quick_turn_45_tool", 20, 0),
        TURN_90("quick_turn_90_tool", 20, 0);

        public final String id;
        public final double defaultFirst;
        public final double defaultSecond;

        Kind(String id, double first, double second) {
            this.id = id;
            defaultFirst = first;
            defaultSecond = second;
        }

        public boolean isTurn() { return this == TURN_45 || this == TURN_90; }
    }

    public record Settings(double first, double second) {}

    private QuickRouteGeometry() {}

    public static Settings defaults(Kind kind) {
        return new Settings(kind.defaultFirst, kind.defaultSecond);
    }

    public static void validate(Kind kind, Settings settings) {
        double first = settings.first(), second = settings.second();
        if (!Double.isFinite(first) || !Double.isFinite(second))
            throw new UserFacingException("error.rrce.quick_number");
        if (kind.isTurn()) {
            double minimum = kind == Kind.TURN_90 ? 8 : 5;
            if (Math.abs(first) < minimum || Math.abs(first) > 128 || Math.abs(second) > 128)
                throw new UserFacingException("error.rrce.quick_turn_range");
        } else if (kind == Kind.CURVE) {
            if (first < 6 || first > 256 || Math.abs(second) > 1)
                throw new UserFacingException("error.rrce.quick_curve_range");
        } else if (first < 6 || first > 256 || Math.abs(second) > .33)
            throw new UserFacingException("error.rrce.quick_slope_range");
    }

    public static SurveyPoint endpoint(SurveyPoint start, Kind kind, Settings settings) {
        validate(kind, settings);
        Vec3 forward = start.heading().vector();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double x, z;
        int rise = 0;
        TrackHeading heading = start.heading();
        if (kind == Kind.CURVE) {
            Vec3 shift = forward.scale(settings.first()).add(right.scale(settings.first() * settings.second()));
            x = shift.x; z = shift.z;
        } else if (kind == Kind.SLOPE) {
            Vec3 shift = forward.scale(settings.first());
            x = shift.x; z = shift.z;
            rise = (int) Math.round(settings.first() * settings.second());
        } else {
            int sign = settings.first() > 0 ? 1 : -1;
            double radius = Math.abs(settings.first());
            double angle = kind == Kind.TURN_45 ? Math.PI / 4 : Math.PI / 2;
            heading = heading.rotate(sign * (kind == Kind.TURN_45 ? 1 : 2));
            Vec3 arrival = heading.vector();
            Vec3 shift = forward.scale(radius * Math.sin(angle))
                .add(right.scale(sign * radius * (1 - Math.cos(angle))));
            double extension = settings.second();
            shift = shift.add((extension >= 0 ? forward : arrival).scale(Math.abs(extension)));
            x = shift.x; z = shift.z;
        }
        BlockPos p = start.pos();
        return new SurveyPoint(new BlockPos(p.getX() + (int) Math.round(x), p.getY() + rise,
            p.getZ() + (int) Math.round(z)), heading);
    }
}
