package dev.sjimo.rrce.client;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.ponder.PonderPalette;
import com.simibubi.create.foundation.ponder.PonderRegistrationHelper;
import com.simibubi.create.foundation.ponder.SceneBuilder;
import com.simibubi.create.foundation.ponder.SceneBuildingUtil;
import com.simibubi.create.foundation.ponder.element.InputWindowElement;
import com.simibubi.create.foundation.utility.Pointing;
import com.simibubi.create.foundation.utility.Couple;
import dev.sjimo.rrce.ConstructionConfig;
import dev.sjimo.rrce.RrceMod;
import dev.sjimo.rrce.world.RoadbedGeometry;
import dev.sjimo.rrce.world.TrackHeading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ponder lessons with a dedicated empty stage; each structure is built during its scene. */
public final class RrcePonder {
    private static final ResourceLocation STAGE = new ResourceLocation(RrceMod.ID, "railway_stage");
    private static final BlockState FOUNDATION = new ConstructionConfig().foundationState();

    private RrcePonder() { }

    public static void register() {
        PonderRegistrationHelper helper = new PonderRegistrationHelper(RrceMod.ID);
        ResourceLocation plan = new ResourceLocation(RrceMod.ID, "construction_plan");
        helper.addStoryBoard(plan, STAGE, RrcePonder::survey);
        helper.addStoryBoard(plan, STAGE, RrcePonder::plan);
        helper.addStoryBoard(plan, STAGE, RrcePonder::cut);
        helper.addStoryBoard(plan, STAGE, RrcePonder::tunnel);
        helper.addStoryBoard(plan, STAGE, RrcePonder::quickRoutes);
    }

    private static void stage(SceneBuilder scene, float scale) {
        scene.configureBasePlate(0, 0, 15);
        scene.scaleSceneView(scale);
        scene.showBasePlate();
        scene.idle(15);
    }

    private static BlockState rail() {
        return AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, TrackShape.XO);
    }

    private static void railway(SceneBuilder scene, SceneBuildingUtil util, int fromX, int toX,
                                boolean reveal) {
        var bed = util.select.fromTo(fromX, 1, 3, toX, 1, 11);
        var left = util.select.fromTo(fromX, 2, 5, toX, 2, 5);
        var right = util.select.fromTo(fromX, 2, 9, toX, 2, 9);
        scene.world.setBlocks(bed, FOUNDATION, false);
        if (reveal) scene.world.showSection(bed, Direction.UP);
        scene.idle(15);
        scene.world.setBlocks(left, rail(), false);
        scene.world.setBlocks(right, rail(), false);
        if (reveal) {
            scene.world.showSection(left, Direction.UP);
            scene.world.showSection(right, Direction.UP);
        }
    }

    private static void survey(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("survey", "Surveying a Railway");
        stage(scene, .68f);
        var first = util.vector.topOf(2, 0, 5);
        var second = util.vector.topOf(12, 0, 5);
        var moved = util.vector.topOf(3, 0, 5);

        scene.overlay.showText(75).pointAt(first).placeNearTarget().colored(PonderPalette.GREEN)
            .text("Right-click with the Add Control Point Tool to mark a rail end. Your view sets its direction.");
        scene.overlay.showControls(new InputWindowElement(first, Pointing.DOWN).rightClick()
            .withItem(RrceMod.TOOL.getDefaultInstance()), 55);
        scene.overlay.showOutline(PonderPalette.GREEN, "first", util.select.position(2, 1, 5), 75);
        scene.idle(75);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(second).placeNearTarget()
            .text("Add another point to complete a section. New points go after the selected point.");
        scene.overlay.showControls(new InputWindowElement(second, Pointing.DOWN).rightClick()
            .withItem(RrceMod.TOOL.getDefaultInstance()), 55);
        scene.overlay.showOutline(PonderPalette.GREEN, "second", util.select.position(12, 1, 5), 75);
        scene.idle(75);

        scene.addKeyframe();
        scene.overlay.showText(65).pointAt(first).placeNearTarget()
            .text("Right-click a control point with the Select Tool. Repeated clicks cycle points in the same block.");
        scene.overlay.showControls(new InputWindowElement(first, Pointing.DOWN).rightClick()
            .withItem(RrceMod.SELECT.getDefaultInstance()), 55);
        scene.overlay.showOutline(PonderPalette.BLUE, "selected", util.select.position(2, 1, 5), 65);
        scene.idle(65);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(moved).placeNearTarget()
            .text("Right-click a new location with the Move Tool to move the selected point.");
        scene.overlay.showControls(new InputWindowElement(moved, Pointing.DOWN).rightClick()
            .withItem(RrceMod.MOVE.getDefaultInstance()), 55);
        scene.overlay.showOutline(PonderPalette.BLUE, "moved", util.select.position(3, 1, 5), 75);
        scene.idle(75);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(moved).placeNearTarget()
            .text("The Direction Tool turns the selected point by 45 degrees. Sneak-right-click to turn the other way.");
        scene.overlay.showControls(new InputWindowElement(moved, Pointing.DOWN).rightClick()
            .withItem(RrceMod.DIRECTION.getDefaultInstance()), 32);
        scene.idle(35);
        scene.overlay.showControls(new InputWindowElement(moved, Pointing.DOWN).rightClick().whileSneaking()
            .withItem(RrceMod.DIRECTION.getDefaultInstance()), 35);
        scene.idle(40);

        scene.addKeyframe();
        scene.overlay.showText(65).pointAt(moved).placeNearTarget()
            .text("Right-click with the Remove Tool to delete the selected point.");
        scene.overlay.showControls(new InputWindowElement(moved, Pointing.DOWN).rightClick()
            .withItem(RrceMod.REMOVE.getDefaultInstance()), 55);
        scene.idle(65);
    }

    private static void plan(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("plan", "Planning and Building Parallel Tracks");
        stage(scene, .60f);
        var target = util.vector.topOf(7, 0, 7);

        scene.overlay.showText(80).pointAt(target).placeNearTarget().colored(PonderPalette.GREEN)
            .text("Two rail ends define a section. The plan extends it to parallel tracks with the chosen spacing.");
        scene.overlay.showOutline(PonderPalette.GREEN, "bed", util.select.fromTo(2, 1, 3, 12, 1, 11), 80);
        scene.idle(80);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(target).placeNearTarget()
            .text("Right-click the Construction Plan to choose the number of lines, clear spacing, rail material and foundation.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick()
            .withItem(RrceMod.PLAN.getDefaultInstance()), 55);
        scene.idle(75);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(target).placeNearTarget()
            .text("Preview the entire roadbed before building. This example uses two lines with three empty blocks between them.");
        scene.idle(75);

        railway(scene, util, 2, 12, true);
        scene.idle(15);
        scene.overlay.showText(90).pointAt(util.vector.topOf(7, 2, 5)).placeNearTarget()
            .colored(PonderPalette.GREEN)
            .text("Construction places a full-width foundation, then both tracks. The roadbed also reaches both ends.");
        scene.idle(90);

        scene.addKeyframe();
        scene.world.setBlocks(util.select.fromTo(2, 2, 5, 12, 2, 5), Blocks.AIR.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(2, 2, 9, 12, 2, 9), Blocks.AIR.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(2, 1, 3, 12, 1, 11), Blocks.AIR.defaultBlockState(), false);
        scene.overlay.showText(75).pointAt(target).placeNearTarget()
            .text("Undo restores the area before construction. Redo builds the same railway again.");
        scene.idle(75);
        railway(scene, util, 2, 12, false);
        scene.idle(35);
    }

    private static void cut(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("cut", "Cutting Through a Hill");
        stage(scene, .57f);
        scene.world.setBlocks(util.select.fromTo(3, 1, 1, 11, 4, 13),
            Blocks.STONE.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(3, 5, 1, 11, 5, 13),
            Blocks.GRASS_BLOCK.defaultBlockState(), false);
        scene.world.showSection(util.select.fromTo(3, 1, 1, 11, 5, 13), Direction.UP);
        scene.idle(25);

        scene.overlay.showText(85).pointAt(util.vector.topOf(7, 5, 7)).placeNearTarget()
            .text("When an obstacle stays below the tunnel threshold, construction cuts through it.");
        scene.overlay.showControls(new InputWindowElement(util.vector.topOf(7, 5, 7), Pointing.DOWN).rightClick()
            .withItem(RrceMod.PLAN.getDefaultInstance()), 55);
        scene.idle(85);

        scene.addKeyframe();
        scene.world.setBlocks(util.select.fromTo(3, 1, 3, 11, 5, 11), Blocks.AIR.defaultBlockState(), true);
        scene.world.setBlocks(util.select.fromTo(3, 4, 2, 11, 5, 2), Blocks.AIR.defaultBlockState(), true);
        scene.world.setBlocks(util.select.fromTo(3, 4, 12, 11, 5, 12), Blocks.AIR.defaultBlockState(), true);
        scene.world.setBlocks(util.select.fromTo(3, 3, 2, 11, 3, 2),
            Blocks.GRASS_BLOCK.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(3, 3, 12, 11, 3, 12),
            Blocks.GRASS_BLOCK.defaultBlockState(), false);
        scene.idle(25);
        railway(scene, util, 3, 11, false);
        scene.idle(20);

        scene.overlay.showText(95).pointAt(util.vector.topOf(7, 2, 5)).placeNearTarget()
            .colored(PonderPalette.GREEN)
            .text("The cut exposes the full roadbed and slopes upward only to its sides, not beyond the rail ends.");
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay.showText(80).pointAt(util.vector.topOf(7, 3, 2)).placeNearTarget()
            .text("Set the slope angle and obstacle threshold in the Construction Plan.");
        scene.idle(80);
    }

    private static void tunnel(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("tunnel", "Building an Open Tunnel");
        stage(scene, .52f);
        scene.world.setBlocks(util.select.fromTo(3, 1, 1, 11, 8, 13),
            Blocks.DEEPSLATE.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(3, 9, 1, 11, 9, 13),
            Blocks.GRASS_BLOCK.defaultBlockState(), false);
        scene.world.showSection(util.select.fromTo(3, 1, 1, 11, 9, 13), Direction.UP);
        scene.rotateCameraY(90);
        scene.idle(30);

        var mouth = util.vector.topOf(3, 1, 7);
        scene.overlay.showText(90).pointAt(mouth).placeNearTarget()
            .text("When terrain rises above the threshold, construction bores a tunnel instead.");
        scene.overlay.showControls(new InputWindowElement(mouth, Pointing.DOWN).rightClick()
            .withItem(RrceMod.PLAN.getDefaultInstance()), 55);
        scene.idle(90);

        scene.addKeyframe();
        scene.world.setBlocks(util.select.fromTo(3, 2, 3, 11, 7, 11), Blocks.AIR.defaultBlockState(), true);
        scene.world.setBlocks(util.select.fromTo(3, 2, 2, 11, 7, 2), Blocks.STONE.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(3, 2, 12, 11, 7, 12), Blocks.STONE.defaultBlockState(), false);
        scene.world.setBlocks(util.select.fromTo(3, 8, 2, 11, 8, 12), Blocks.STONE.defaultBlockState(), false);
        railway(scene, util, 3, 11, false);
        scene.idle(35);

        scene.overlay.showText(95).pointAt(mouth).placeNearTarget()
            .colored(PonderPalette.GREEN)
            .text("The tracks pass through a square tunnel. With zero side clearance, the walls border the roadbed.");
        scene.idle(95);

        scene.addKeyframe();
        scene.rotateCameraY(180);
        scene.idle(25);
        scene.overlay.showText(85).pointAt(util.vector.topOf(11, 1, 7)).placeNearTarget()
            .text("The plan controls clearance, wall thickness and wall material. Server terrain rules decide what may be dug.");
        scene.idle(85);
    }

    private static void quickRoutes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("quick_routes", "Extending a Surveyed Route");
        stage(scene, .59f);
        var target = util.vector.topOf(5, 2, 6);
        scene.overlay.showText(75).pointAt(target).placeNearTarget().colored(PonderPalette.GREEN)
            .text("Select a rail end. A quick planning tool adds the next end along its forward direction.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick()
            .withItem(RrceMod.QUICK_CURVE.getDefaultInstance()), 55);
        quickPath(scene, util, new BlockPos(2, 2, 5), TrackHeading.EAST,
            new BlockPos(12, 2, 8), TrackHeading.EAST);
        scene.idle(75);

        scene.addKeyframe();
        scene.overlay.showText(75).pointAt(target).placeNearTarget()
            .text("The curve tool shifts the route sideways. Sneak-right-click to adjust its reach and offset.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick().whileSneaking()
            .withItem(RrceMod.QUICK_CURVE.getDefaultInstance()), 55);
        scene.idle(75);

        scene.addKeyframe();
        hideQuickPath(scene, util);
        quickPath(scene, util, new BlockPos(2, 2, 6), TrackHeading.EAST,
            new BlockPos(12, 4, 6), TrackHeading.EAST);
        scene.overlay.showText(85).pointAt(util.vector.topOf(9, 4, 6)).placeNearTarget()
            .text("The slope tool raises or lowers the next rail end while keeping the route straight.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick()
            .withItem(RrceMod.QUICK_SLOPE.getDefaultInstance()), 55);
        scene.idle(85);

        scene.addKeyframe();
        hideQuickPath(scene, util);
        quickPath(scene, util, new BlockPos(2, 2, 4), TrackHeading.EAST,
            new BlockPos(12, 2, 8), TrackHeading.SOUTH_EAST);
        scene.overlay.showText(85).pointAt(util.vector.topOf(8, 2, 8)).placeNearTarget()
            .text("The 45-degree tool turns the next rail end by one direction step.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick()
            .withItem(RrceMod.QUICK_TURN_45.getDefaultInstance()), 55);
        scene.idle(85);

        scene.addKeyframe();
        hideQuickPath(scene, util);
        quickPath(scene, util, new BlockPos(2, 2, 3), TrackHeading.EAST,
            new BlockPos(12, 2, 13), TrackHeading.SOUTH);
        scene.overlay.showText(90).pointAt(util.vector.topOf(9, 2, 9)).placeNearTarget()
            .text("The 90-degree tool turns by two steps. Set the signed radius to choose left or right.");
        scene.overlay.showControls(new InputWindowElement(target, Pointing.DOWN).rightClick()
            .withItem(RrceMod.QUICK_TURN_90.getDefaultInstance()), 55);
        scene.idle(90);
    }

    private static void hideQuickPath(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world.hideSection(util.select.fromTo(1, 1, 1, 13, 5, 13), Direction.DOWN);
        scene.idle(20);
    }

    /** Use Create's real connection data, rather than a row of disconnected track blocks. */
    private static void quickPath(SceneBuilder scene, SceneBuildingUtil util, BlockPos first,
                                  TrackHeading firstHeading, BlockPos last, TrackHeading lastHeading) {
        scene.world.setBlocks(util.select.fromTo(1, 1, 1, 13, 5, 13), Blocks.AIR.defaultBlockState(), false);
        Vec3 a = firstHeading.vector(), b = lastHeading.vector();
        BezierConnection connection = new BezierConnection(Couple.create(first, last),
            Couple.create(curveStart(first, firstHeading.rawAxis()),
                curveStart(last, lastHeading.rawAxis().scale(-1))),
            Couple.create(a, b.scale(-1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
            true, false, TrackMaterial.ANDESITE);
        ConstructionConfig roadbed = new ConstructionConfig();
        roadbed.resizeLines(1);
        roadbed.edgeMargin = 1;
        List<RoadbedGeometry.Sample> samples = new ArrayList<>();
        RoadbedGeometry.addCurveSamples(samples, first, a, connection, last, b, 0);
        RoadbedGeometry.foundationLayout(samples, roadbed, 1).blocks()
            .forEach((position, state) -> scene.world.setBlock(position, state, false));
        scene.world.setBlock(first, track(firstHeading), false);
        scene.world.setBlock(last, track(lastHeading), false);
        scene.world.modifyBlockEntityNBT(util.select.position(first), TrackBlockEntity.class, tag -> {
            ListTag curves = new ListTag();
            curves.add(connection.write(first));
            tag.put("Connections", curves);
        }, true);
        scene.world.modifyBlockEntityNBT(util.select.position(last), TrackBlockEntity.class, tag -> {
            ListTag curves = new ListTag();
            curves.add(connection.secondary().write(last));
            tag.put("Connections", curves);
        }, true);
        scene.world.showSection(util.select.fromTo(1, 1, 1, 13, 5, 13), Direction.UP);
        scene.idle(18);
    }

    private static Vec3 curveStart(BlockPos point, Vec3 outward) {
        return Vec3.atCenterOf(point).add(0, -.5, 0).add(outward.scale(.5));
    }

    private static BlockState track(TrackHeading heading) {
        TrackShape shape = heading.dx == 0 ? TrackShape.ZO : heading.dz == 0 ? TrackShape.XO
            : heading.dx == heading.dz ? TrackShape.PD : TrackShape.ND;
        // Ponder can only load and render Bezier connections from a real track block entity.
        return AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, shape)
            .setValue(TrackBlock.HAS_BE, true);
    }
}
