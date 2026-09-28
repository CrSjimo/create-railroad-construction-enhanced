package dev.sjimo.rrce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PlayerSessionTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        dev.sjimo.rrce.TestBootstrap.bootStrap();
    }

    @Test void insertsAfterSelectionAndCyclesPointsInTheClickedBlock() {
        PlayerSession session = new PlayerSession();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos middle = new BlockPos(10, 64, 0);
        BlockPos last = new BlockPos(20, 64, 0);
        session.addPoint(first, TrackHeading.EAST);
        session.addPoint(last, TrackHeading.EAST);
        session.select(0);
        session.addPoint(middle, TrackHeading.EAST);
        assertEquals(1, session.selectedPoint);
        assertEquals(middle, session.points.get(1).pos());
        session.addPoint(new BlockPos(15, 64, 0), TrackHeading.EAST);
        session.moveSelected(first);
        assertEquals(0, session.selectNextAt(first));
        assertEquals(2, session.selectNextAt(first));
        assertThrows(UserFacingException.class, () -> session.selectNextAt(new BlockPos(30, 64, 0)));
    }

    @Test void rejectsAdjacentOverlapsWithoutChangingTheRoute() {
        PlayerSession session = new PlayerSession();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        session.addPoint(first, TrackHeading.EAST);
        assertThrows(UserFacingException.class, () -> session.addPoint(first, TrackHeading.EAST));
        session.addPoint(second, TrackHeading.EAST);
        assertThrows(UserFacingException.class, () -> session.moveSelected(first));
        assertEquals(second, session.points.get(1).pos());
    }

    @Test void restoresOnlyTheLatestBuiltRouteOnce() {
        PlayerSession session = new PlayerSession();
        session.addPoint(new BlockPos(0, 64, 0), TrackHeading.EAST);
        session.addPoint(new BlockPos(20, 64, 0), TrackHeading.EAST);
        session.select(0);
        session.savePointsForNextUndo();
        session.points.clear();
        session.selectedPoint = -1;

        session.addPoint(new BlockPos(5, 64, 5), TrackHeading.SOUTH);
        session.addPoint(new BlockPos(5, 64, 25), TrackHeading.SOUTH);
        session.savePointsForNextUndo();
        session.points.clear();
        session.selectedPoint = -1;

        assertTrue(session.restorePointsAfterUndo());
        assertEquals(2, session.points.size());
        assertEquals(new BlockPos(5, 64, 5), session.points.get(0).pos());
        assertEquals(TrackHeading.SOUTH, session.points.get(1).heading());
        assertEquals(1, session.selectedPoint);

        session.select(0);
        assertFalse(session.restorePointsAfterUndo());
        assertEquals(0, session.selectedPoint);
    }

    @Test void oneToolActionIsAcceptedUntilTheUseKeyIsReleased() {
        PlayerSession session = new PlayerSession();
        assertTrue(session.claimToolPress());
        assertFalse(session.claimToolPress());
        session.releaseToolPress();
        assertTrue(session.claimToolPress());
    }
}
