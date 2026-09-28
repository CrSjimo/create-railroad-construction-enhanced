package dev.sjimo.rrce;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.sjimo.rrce.world.EditHistory;
import dev.sjimo.rrce.world.SurveyPoint;
import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class PlayerSession {
    public final List<SurveyPoint> points = new ArrayList<>();
    public ConstructionConfig config = new ConstructionConfig();
    public boolean previewEnabled = true;
    public boolean fullPreview;
    public int selectedPoint = -1;
    public int previewRevision;
    public final Deque<EditHistory> undo = new ArrayDeque<>();
    public final Deque<EditHistory> redo = new ArrayDeque<>();
    public Instant disconnectedAt;
    private List<SurveyPoint> pointsBeforeLatestBuild;
    private int selectedBeforeLatestBuild;
    private EditHistory latestBuildForPointRestore;
    private boolean toolPressHeld;

    public record UndoResult(EditHistory edit, boolean pointsRestored) {}

    boolean claimToolPress() {
        if (toolPressHeld) return false;
        toolPressHeld = true;
        return true;
    }

    void releaseToolPress() { toolPressHeld = false; }

    public void addPoint(BlockPos point, TrackHeading heading) {
        if (points.size() >= 64) throw new UserFacingException("error.rrce.too_many_points");
        int insertion = selectedPoint < 0 ? points.size() : selectedPoint + 1;
        if (insertion > 0 && points.get(insertion - 1).pos().equals(point)
            || insertion < points.size() && points.get(insertion).pos().equals(point))
            throw new UserFacingException("error.rrce.duplicate_point");
        points.add(insertion, new SurveyPoint(point, heading));
        selectedPoint = insertion;
    }

    public void select(int index) {
        if (index < 0 || index >= points.size()) throw new UserFacingException("error.rrce.invalid_point_index");
        selectedPoint = index;
    }

    public void moveSelected(BlockPos point) {
        select(selectedPoint);
        if (selectedPoint > 0 && points.get(selectedPoint - 1).pos().equals(point)
            || selectedPoint + 1 < points.size() && points.get(selectedPoint + 1).pos().equals(point))
            throw new UserFacingException("error.rrce.duplicate_point");
        points.set(selectedPoint, points.get(selectedPoint).at(point));
    }

    public int selectNextAt(BlockPos position) {
        if (points.isEmpty()) throw new UserFacingException("error.rrce.no_point_here");
        for (int offset = 1; offset <= points.size(); offset++) {
            int index = Math.floorMod(selectedPoint + offset, points.size());
            if (points.get(index).pos().equals(position)) {
                selectedPoint = index;
                return index;
            }
        }
        throw new UserFacingException("error.rrce.no_point_here");
    }

    public void rotateSelected(int steps) {
        select(selectedPoint);
        SurveyPoint selected = points.get(selectedPoint);
        points.set(selectedPoint, selected.facing(selected.heading().rotate(steps)));
    }

    public void removeSelected() {
        select(selectedPoint);
        if (selectedPoint > 0 && selectedPoint + 1 < points.size()
            && points.get(selectedPoint - 1).pos().equals(points.get(selectedPoint + 1).pos()))
            throw new UserFacingException("error.rrce.duplicate_point");
        points.remove(selectedPoint);
        selectedPoint = points.isEmpty() ? -1 : Math.min(selectedPoint, points.size() - 1);
    }

    public void remember(EditHistory edit) {
        undo.addLast(edit);
        redo.clear();
        while (undo.size() > 15) undo.removeFirst();
        savePointsForNextUndo();
        latestBuildForPointRestore = edit;
    }

    /** Called only after a successful build; a later undo may consume this once. */
    void savePointsForNextUndo() {
        pointsBeforeLatestBuild = List.copyOf(points);
        selectedBeforeLatestBuild = selectedPoint;
    }

    /** The next successful undo restores the last built route; later history edits do not. */
    boolean restorePointsAfterUndo() {
        List<SurveyPoint> saved = pointsBeforeLatestBuild;
        pointsBeforeLatestBuild = null;
        if (saved == null) return false;
        points.clear();
        points.addAll(saved);
        selectedPoint = selectedBeforeLatestBuild;
        return true;
    }

    public UndoResult undoConstruction(ServerLevel level) {
        if (undo.isEmpty()) throw new UserFacingException("error.rrce.nothing_to_undo");
        EditHistory edit = undo.removeLast();
        try { edit.undo(level); }
        catch (RuntimeException error) { undo.addLast(edit); throw error; }
        redo.addLast(edit);
        boolean restore = edit == latestBuildForPointRestore && restorePointsAfterUndo();
        latestBuildForPointRestore = null;
        pointsBeforeLatestBuild = null;
        return new UndoResult(edit, restore);
    }

    public EditHistory redoConstruction(ServerLevel level) {
        if (redo.isEmpty()) throw new UserFacingException("error.rrce.nothing_to_redo");
        EditHistory edit = redo.removeLast();
        try { edit.redo(level); }
        catch (RuntimeException error) { redo.addLast(edit); throw error; }
        undo.addLast(edit);
        return edit;
    }
}
