package dev.sjimo.rrce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.sjimo.rrce.world.RouteGeometry;
import dev.sjimo.rrce.world.SurveyPoint;
import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.core.BlockPos;

class QuickRouteGeometryTest {
    private static final SurveyPoint EAST = new SurveyPoint(new BlockPos(0, 64, 0), TrackHeading.EAST);

    @Test
    void defaultsProduceBuildableParallelSections() {
        ConstructionConfig config = new ConstructionConfig();
        for (TrackHeading heading : TrackHeading.values()) {
            SurveyPoint start = new SurveyPoint(EAST.pos(), heading);
            for (QuickRouteGeometry.Kind kind : QuickRouteGeometry.Kind.values()) {
                QuickRouteGeometry.Settings defaults = QuickRouteGeometry.defaults(kind);
                for (int sign : kind.isTurn() ? new int[] {1, -1} : new int[] {1}) {
                    SurveyPoint end = QuickRouteGeometry.endpoint(start, kind,
                        new QuickRouteGeometry.Settings(defaults.first() * sign, defaults.second()));
                    RouteGeometry.buildLayout(List.of(start, end), config);
                }
            }
        }
    }

    @Test
    void offsetsAndGradeFollowTheStartDirection() {
        assertEquals(new SurveyPoint(new BlockPos(20, 64, 1), TrackHeading.EAST),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.CURVE,
                new QuickRouteGeometry.Settings(20, .05)));
        assertEquals(new SurveyPoint(new BlockPos(20, 65, 0), TrackHeading.EAST),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.SLOPE,
                new QuickRouteGeometry.Settings(20, .05)));
        assertEquals(new BlockPos(20, 63, 0), QuickRouteGeometry.endpoint(EAST,
            QuickRouteGeometry.Kind.SLOPE, new QuickRouteGeometry.Settings(20, -.05)).pos());
    }

    @Test
    void turnsRespectSideAndStraightExtension() {
        assertEquals(new SurveyPoint(new BlockPos(14, 64, 6), TrackHeading.SOUTH_EAST),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.TURN_45,
                new QuickRouteGeometry.Settings(20, 0)));
        assertEquals(new SurveyPoint(new BlockPos(14, 64, -6), TrackHeading.NORTH_EAST),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.TURN_45,
                new QuickRouteGeometry.Settings(-20, 0)));
        assertEquals(new SurveyPoint(new BlockPos(25, 64, 20), TrackHeading.SOUTH),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.TURN_90,
                new QuickRouteGeometry.Settings(20, 5)));
        assertEquals(new SurveyPoint(new BlockPos(20, 64, 25), TrackHeading.SOUTH),
            QuickRouteGeometry.endpoint(EAST, QuickRouteGeometry.Kind.TURN_90,
                new QuickRouteGeometry.Settings(20, -5)));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(UserFacingException.class, () -> QuickRouteGeometry.validate(
            QuickRouteGeometry.Kind.CURVE, new QuickRouteGeometry.Settings(Double.NaN, 0)));
        assertThrows(UserFacingException.class, () -> QuickRouteGeometry.validate(
            QuickRouteGeometry.Kind.TURN_90, new QuickRouteGeometry.Settings(0, 0)));
    }
}
