package dev.sjimo.rrce.world;

import java.util.Locale;

import dev.sjimo.rrce.UserFacingException;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Horizontal rail direction, pointing toward increasing survey point numbers. */
public enum TrackHeading {
    EAST(1, 0), SOUTH_EAST(1, 1), SOUTH(0, 1), SOUTH_WEST(-1, 1),
    WEST(-1, 0), NORTH_WEST(-1, -1), NORTH(0, -1), NORTH_EAST(1, -1);

    public final int dx;
    public final int dz;

    TrackHeading(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Component displayName() {
        return Component.translatable("direction.rrce." + name().toLowerCase(Locale.ROOT));
    }
    public Vec3 vector() { return new Vec3(dx, 0, dz).normalize(); }
    public Vec3 rawAxis() { return new Vec3(dx, 0, dz); }
    public TrackHeading rotate(int steps) {
        return values()[Math.floorMod(ordinal() + steps, values().length)];
    }
    public static TrackHeading fromVector(Vec3 vector) {
        TrackHeading best = EAST;
        double score = -Double.MAX_VALUE;
        Vec3 flat = new Vec3(vector.x, 0, vector.z).normalize();
        for (TrackHeading heading : values()) {
            double candidate = flat.dot(heading.vector());
            if (candidate > score) { score = candidate; best = heading; }
        }
        return best;
    }
    public static TrackHeading byIndex(int index) {
        if (index < 0 || index >= values().length) throw new UserFacingException("error.rrce.invalid_heading");
        return values()[index];
    }
}
